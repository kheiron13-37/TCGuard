package main

import (
	"sync"
	"time"
)

type bucket struct {
	tokens, rate, burst float64
	last                time.Time
}

func newBucket(rate, burst float64) *bucket {
	return &bucket{tokens: burst, rate: rate, burst: burst, last: time.Now()}
}

func (b *bucket) take(n float64, now time.Time) bool {
	b.tokens = min(b.burst, b.tokens+now.Sub(b.last).Seconds()*b.rate)
	b.last = now
	if b.tokens < n {
		return false
	}
	b.tokens -= n
	return true
}

type peer struct {
	open    int
	connect *bucket
}

type limiter struct {
	mu     sync.Mutex
	peers  map[string]*peer
	logins *bucket
	cfg    *Config
}

func newLimiter(cfg *Config) *limiter {
	return &limiter{peers: map[string]*peer{}, logins: newBucket(cfg.LoginsPerSecond, cfg.LoginsPerSecond), cfg: cfg}
}

func (l *limiter) open(ip string) string {
	l.mu.Lock()
	defer l.mu.Unlock()
	p := l.peers[ip]
	if p == nil {
		p = &peer{connect: newBucket(l.cfg.PerIP.Rate, l.cfg.PerIP.Burst)}
		l.peers[ip] = p
	}
	if p.open >= l.cfg.PerIP.Connections {
		return "too many open connections"
	}
	if !p.connect.take(1, time.Now()) {
		return "connecting too fast"
	}
	p.open++
	return ""
}

func (l *limiter) close(ip string) {
	l.mu.Lock()
	l.peers[ip].open--
	l.mu.Unlock()
}

func (l *limiter) login() bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	return l.logins.take(1, time.Now())
}

func (l *limiter) sweep() {
	now := time.Now()
	l.mu.Lock()
	for ip, p := range l.peers {
		if p.open == 0 && p.connect.take(0, now) && p.connect.tokens >= p.connect.burst {
			delete(l.peers, ip)
		}
	}
	l.mu.Unlock()
}
