package main

import (
	"bufio"
	"bytes"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/binary"
	"io"
	"net"
	"strings"
	"testing"
	"time"
)

func TestVarInt(t *testing.T) {
	for _, v := range []int{0, 1, 127, 128, 255, 25565, 2097151, 2147483647, -1} {
		got, err := readVarInt(bytes.NewReader(appendVarInt(nil, v)))
		if err != nil || int32(got) != int32(v) {
			t.Errorf("%d: got %d, %v", v, got, err)
		}
	}
	if _, err := readVarInt(bytes.NewReader([]byte{0xff, 0xff, 0xff, 0xff, 0xff, 0x01})); err == nil {
		t.Error("six-byte varint accepted")
	}
}

func TestHandshake(t *testing.T) {
	h, err := parseHandshake(handshakePacket(47, "play.example.net", 25565, 2))
	if err != nil || h.protocol != 47 || h.host != "play.example.net" || h.next != 2 {
		t.Fatalf("valid handshake: %+v %v", h, err)
	}
	bad := map[string][]byte{
		"next state 3":     handshakePacket(47, "x", 1, 3),
		"host of 300":      handshakePacket(47, strings.Repeat("a", 300), 1, 2),
		"trailing bytes":   append(handshakePacket(47, "x", 1, 2), 0),
		"wrong packet id":  append([]byte{1}, handshakePacket(47, "x", 1, 2)[1:]...),
		"truncated":        handshakePacket(47, "x", 1, 2)[:4],
		"invalid utf8":     append(appendVarInt(appendVarInt([]byte{0}, 47), 2), 0xc3, 0x28, 0, 1, 2),
		"length past data": append(appendVarInt([]byte{0}, 47), 0x7f),
	}
	for name, b := range bad {
		if _, err := parseHandshake(b); err == nil {
			t.Errorf("%s accepted", name)
		}
	}
}

func TestLoginStart(t *testing.T) {
	if name, err := parseLoginStart(stringPacket(0, "Ahmet_01")); err != nil || name != "Ahmet_01" {
		t.Errorf("valid name: %q %v", name, err)
	}
	if _, err := parseLoginStart(stringPacket(0, strings.Repeat("a", 17))); err == nil {
		t.Error("17 characters accepted")
	}
}

func TestReadFrameLimit(t *testing.T) {
	big := frame(make([]byte, 2000))
	if _, err := readFrame(bufio.NewReader(bytes.NewReader(big)), 1024); err == nil {
		t.Error("2000-byte frame accepted with a 1024 limit")
	}
	if _, err := readFrame(bufio.NewReader(bytes.NewReader([]byte{0})), 1024); err == nil {
		t.Error("empty frame accepted")
	}
}

func TestProxyHeader(t *testing.T) {
	src := &net.TCPAddr{IP: net.ParseIP("203.0.113.7"), Port: 51000}
	dst := &net.TCPAddr{IP: net.ParseIP("10.0.0.1"), Port: 25565}
	h := proxyHeader(src, dst, "", time.Time{})
	want := append(append([]byte(nil), proxySignature...), 0x21, 0x11, 0, 12, 203, 0, 113, 7, 10, 0, 0, 1, 0xc7, 0x38, 0x63, 0xdd)
	if !bytes.Equal(h, want) {
		t.Errorf("ipv4 header\n got %x\nwant %x", h, want)
	}
	if h := proxyHeader(&net.TCPAddr{IP: net.ParseIP("2001:db8::1"), Port: 1}, &net.TCPAddr{IP: net.ParseIP("::1"), Port: 2}, "", time.Time{}); len(h) != 16+36 || h[13] != 0x21 {
		t.Errorf("ipv6 header %x", h)
	}
	if h := proxyHeader(nil, nil, "", time.Time{}); len(h) != 16 || h[12] != 0x20 {
		t.Errorf("local header %x", h)
	}
}

func TestBucket(t *testing.T) {
	now := time.Now()
	b := &bucket{tokens: 2, rate: 1, burst: 2, last: now}
	if !b.take(1, now) || !b.take(1, now) || b.take(1, now) {
		t.Fatal("burst of 2 not enforced")
	}
	if !b.take(1, now.Add(1100*time.Millisecond)) {
		t.Fatal("no refill after a second")
	}
}

func TestLimiter(t *testing.T) {
	cfg := defaults()
	cfg.PerIP.Connections, cfg.PerIP.Rate, cfg.PerIP.Burst = 2, 0, 3
	l := newLimiter(cfg)
	if l.open("a") != "" || l.open("a") != "" || l.open("a") == "" {
		t.Fatal("open connection limit not enforced")
	}
	l.close("a")
	if l.open("a") != "" || l.open("b") != "" {
		t.Fatal("slot not released or other ip blocked")
	}
	l.close("a")
	if l.open("a") == "" {
		t.Fatal("connect burst not enforced")
	}
}

func fakeBackend(t *testing.T) (string, chan []byte) {
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { ln.Close() })
	got := make(chan []byte, 16)
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go func() {
				defer c.Close()
				c.SetDeadline(time.Now().Add(2 * time.Second))
				r := bufio.NewReader(c)
				header := make([]byte, 16)
				io.ReadFull(r, header)
				io.ReadFull(r, make([]byte, int(header[14])<<8|int(header[15])))
				hs, _ := readFrame(r, 1024)
				next, _ := parseHandshake(hs)
				if next.next == 1 {
					readFrame(r, 1)
					c.Write(frame(stringPacket(0, `{"description":"fake"}`)))
					got <- []byte("status")
					return
				}
				login, _ := readFrame(r, 512)
				got <- append(header, login...)
			}()
		}
	}()
	return ln.Addr().String(), got
}

func startGate(t *testing.T, backend string) string {
	cfg := defaults()
	cfg.PerIP.Burst = 100
	g, err := newGate(cfg, Route{Listen: ":0", Backend: backend})
	if err != nil {
		t.Fatal(err)
	}
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { ln.Close() })
	go func() {
		for {
			c, err := ln.Accept()
			if err != nil {
				return
			}
			go g.serve(c)
		}
	}()
	return ln.Addr().String()
}

func TestStatusIsCached(t *testing.T) {
	backend, got := fakeBackend(t)
	gate := startGate(t, backend)
	for i := 0; i < 20; i++ {
		c, err := net.Dial("tcp", gate)
		if err != nil {
			t.Fatal(err)
		}
		c.Write(append(frame(handshakePacket(47, "x", 1, 1)), frame([]byte{0})...))
		c.SetDeadline(time.Now().Add(2 * time.Second))
		reply, err := readFrame(bufio.NewReader(c), 1<<16)
		c.Close()
		if err != nil || !strings.Contains(string(reply), "fake") {
			t.Fatalf("status %d: %q %v", i, reply, err)
		}
	}
	if n := len(got); n != 1 {
		t.Errorf("backend asked %d times for 20 pings", n)
	}
}

func TestLoginForwardsAddress(t *testing.T) {
	backend, got := fakeBackend(t)
	gate := startGate(t, backend)
	c, err := net.Dial("tcp", gate)
	if err != nil {
		t.Fatal(err)
	}
	defer c.Close()
	c.Write(append(frame(handshakePacket(47, "x", 1, 2)), frame(stringPacket(0, "Ahmet"))...))
	select {
	case b := <-got:
		if !bytes.Equal(b[:12], proxySignature) || b[13] != 0x11 || !bytes.Contains(b, []byte("Ahmet")) {
			t.Errorf("backend received %x", b)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("login never reached the backend")
	}
}

func TestBadNameNeverReachesBackend(t *testing.T) {
	backend, got := fakeBackend(t)
	gate := startGate(t, backend)
	c, _ := net.Dial("tcp", gate)
	c.Write(append(frame(handshakePacket(47, "x", 1, 2)), frame(stringPacket(0, "bad name!"))...))
	c.SetReadDeadline(time.Now().Add(time.Second))
	_, err := c.Read(make([]byte, 1))
	c.Close()
	if err == nil {
		t.Error("connection kept open")
	}
	select {
	case <-got:
		t.Error("backend saw the login")
	case <-time.After(300 * time.Millisecond):
	}
}

func TestRoutes(t *testing.T) {
	single := defaults()
	single.Listen, single.Backend = ":25565", "127.0.0.1:25566"
	if r := single.routes(); len(r) != 1 || r[0].Listen != ":25565" || r[0].Backend != "127.0.0.1:25566" {
		t.Fatalf("single route fallback: %+v", r)
	}
	multi := defaults()
	multi.Routes = []Route{{":25565", "127.0.0.1:30001"}, {":25566", "127.0.0.1:30002"}}
	if r := multi.routes(); len(r) != 2 || r[1].Backend != "127.0.0.1:30002" {
		t.Fatalf("explicit routes: %+v", r)
	}
}

func TestSignedProxyHeader(t *testing.T) {
	src := &net.TCPAddr{IP: net.ParseIP("203.0.113.7"), Port: 51000}
	dst := &net.TCPAddr{IP: net.ParseIP("10.0.0.1"), Port: 25565}
	now := time.UnixMilli(1700000000000)
	h := proxyHeader(src, dst, "s3cret", now)
	if got := int(h[14])<<8 | int(h[15]); got != 12+3+40 || len(h) != 16+got {
		t.Fatalf("length field %d, header %d bytes", got, len(h))
	}
	tlv := h[28:]
	if tlv[0] != tlvSignature || tlv[2] != 40 || binary.BigEndian.Uint64(tlv[3:11]) != 1700000000000 {
		t.Fatalf("tlv %x", tlv)
	}
	mac := hmac.New(sha256.New, []byte("s3cret"))
	mac.Write(h[16:28])
	mac.Write(tlv[3:11])
	if !hmac.Equal(mac.Sum(nil), tlv[11:]) {
		t.Error("hmac does not cover address and timestamp")
	}
	if other := proxyHeader(src, dst, "other", now); bytes.Equal(other, h) {
		t.Error("secret does not change the header")
	}
	if local := proxyHeader(nil, nil, "s3cret", now); len(local) != 16+43 || local[12] != 0x20 {
		t.Errorf("signed local header %x", local)
	}
}
