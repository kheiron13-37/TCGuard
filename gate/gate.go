package main

import (
	"bufio"
	"encoding/json"
	"errors"
	"io"
	"net"
	"regexp"
	"strconv"
	"sync"
	"sync/atomic"
	"time"
)

type Gate struct {
	cfg     *Config
	backend string
	listen  string
	limits  *limiter
	pending chan struct{}
	name    *regexp.Regexp
	status  statusCache
	stats   stats
}

type stats struct {
	accepted, logins, statuses, backendStatus atomic.Int64
	mu                                        sync.Mutex
	refused                                   map[string]int64
}

func (s *stats) refuse(reason string) {
	s.mu.Lock()
	s.refused[reason]++
	s.mu.Unlock()
}

func newGate(cfg *Config, route Route) (*Gate, error) {
	name, err := regexp.Compile(cfg.NamePattern)
	if err != nil {
		return nil, err
	}
	g := &Gate{cfg: cfg, backend: route.Backend, listen: route.Listen, limits: newLimiter(cfg),
		pending: make(chan struct{}, cfg.MaxPending), name: name}
	g.stats.refused = map[string]int64{}
	g.status.entries = map[int]statusEntry{}
	return g, nil
}

func (g *Gate) serve(c net.Conn) {
	defer c.Close()
	g.stats.accepted.Add(1)
	ip := c.RemoteAddr().(*net.TCPAddr).IP.String()
	if reason := g.limits.open(ip); reason != "" {
		g.stats.refuse(reason)
		return
	}
	defer g.limits.close(ip)

	select {
	case g.pending <- struct{}{}:
	default:
		g.stats.refuse("handshake queue full")
		return
	}
	released := false
	release := func() {
		if !released {
			released = true
			<-g.pending
		}
	}
	defer release()

	c.SetDeadline(time.Now().Add(time.Duration(g.cfg.HandshakeTimeout) * time.Second))
	r := bufio.NewReaderSize(c, 1024)
	if first, err := r.Peek(1); err != nil || first[0] == 0xFE {
		g.stats.refuse("legacy ping or no data")
		return
	}
	raw, err := readFrame(r, 1024)
	if err != nil {
		g.stats.refuse("bad handshake frame")
		return
	}
	h, err := parseHandshake(raw)
	if err != nil {
		g.stats.refuse("bad handshake")
		return
	}
	if h.next == 1 {
		g.answerStatus(c, r, h)
		return
	}

	loginStart, err := readFrame(r, 512)
	if err != nil {
		g.stats.refuse("bad login frame")
		return
	}
	name, err := parseLoginStart(loginStart)
	if err != nil || !g.name.MatchString(name) {
		g.stats.refuse("bad name")
		return
	}
	if !g.limits.login() {
		g.stats.refuse("login rate")
		disconnect(c, g.cfg.Messages.Busy)
		return
	}
	backend, err := g.dial(c.RemoteAddr(), c.LocalAddr())
	if err != nil {
		g.stats.refuse("backend down")
		disconnect(c, g.cfg.Messages.Unavailable)
		return
	}
	defer backend.Close()
	buffered, _ := r.Peek(r.Buffered())
	if _, err = backend.Write(append(append(frame(raw), frame(loginStart)...), buffered...)); err != nil {
		return
	}
	r.Discard(len(buffered))
	release()
	g.stats.logins.Add(1)
	g.pipe(c, backend)
}

func (g *Gate) dial(src, dst net.Addr) (net.Conn, error) {
	backend, err := net.DialTimeout("tcp", g.backend, 3*time.Second)
	if err != nil || !g.cfg.ProxyProtocol {
		return backend, err
	}
	var s, d *net.TCPAddr
	if src != nil {
		s, d = src.(*net.TCPAddr), dst.(*net.TCPAddr)
	}
	if _, err = backend.Write(proxyHeader(s, d, g.cfg.Secret, time.Now())); err != nil {
		backend.Close()
		return nil, err
	}
	return backend, nil
}

func (g *Gate) pipe(client, backend net.Conn) {
	idle := time.Duration(g.cfg.IdleTimeout) * time.Second
	client.SetDeadline(time.Time{})
	done := make(chan struct{}, 2)
	go func() {
		io.Copy(client, backend)
		client.Close()
		done <- struct{}{}
	}()
	go func() {
		budget := newBucket(g.cfg.Upload.BytesPerSecond, g.cfg.Upload.Burst)
		buf := make([]byte, 32<<10)
		for {
			client.SetReadDeadline(time.Now().Add(idle))
			n, err := client.Read(buf)
			if n > 0 {
				if !budget.take(float64(n), time.Now()) {
					g.stats.refuse("upload limit")
					break
				}
				if _, werr := backend.Write(buf[:n]); werr != nil {
					break
				}
			}
			if err != nil {
				break
			}
		}
		backend.Close()
		done <- struct{}{}
	}()
	<-done
	<-done
}

func disconnect(c net.Conn, message string) {
	text, _ := json.Marshal(map[string]string{"text": message})
	c.Write(frame(stringPacket(0, string(text))))
}

type statusEntry struct {
	json string
	at   time.Time
}

type statusCache struct {
	mu      sync.Mutex
	entries map[int]statusEntry
}

func (g *Gate) answerStatus(c net.Conn, r *bufio.Reader, h handshake) {
	if request, err := readFrame(r, 1); err != nil || request[0] != 0 {
		g.stats.refuse("bad status request")
		return
	}
	response, err := g.cachedStatus(h.protocol)
	if err != nil {
		g.stats.refuse("backend down")
		return
	}
	g.stats.statuses.Add(1)
	if _, err = c.Write(frame(stringPacket(0, response))); err != nil {
		return
	}
	ping, err := readFrame(r, 9)
	if err != nil || len(ping) != 9 || ping[0] != 1 {
		return
	}
	c.Write(frame(ping))
}

func (g *Gate) cachedStatus(protocol int) (string, error) {
	g.status.mu.Lock()
	defer g.status.mu.Unlock()
	if e, ok := g.status.entries[protocol]; ok && time.Since(e.at) < time.Duration(g.cfg.StatusCache)*time.Second {
		return e.json, nil
	}
	backend, err := g.dial(nil, nil)
	if err != nil {
		return "", err
	}
	defer backend.Close()
	backend.SetDeadline(time.Now().Add(3 * time.Second))
	host, port, _ := net.SplitHostPort(g.backend)
	p, _ := strconv.Atoi(port)
	if _, err = backend.Write(append(frame(handshakePacket(protocol, host, uint16(p), 1)), frame([]byte{0})...)); err != nil {
		return "", err
	}
	g.stats.backendStatus.Add(1)
	raw, err := readFrame(bufio.NewReader(backend), 1<<16)
	if err != nil {
		return "", err
	}
	pk := &packet{b: raw}
	if id, err := pk.varInt(); err != nil || id != 0 {
		return "", errors.New("unexpected status reply")
	}
	response, err := pk.string(32767)
	if err != nil {
		return "", err
	}
	g.status.entries[protocol] = statusEntry{response, time.Now()}
	return response, nil
}
