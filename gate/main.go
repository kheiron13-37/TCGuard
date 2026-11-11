package main

import (
	"flag"
	"fmt"
	"log"
	"net"
	"sort"
	"strings"
	"time"
)

var version = "1.2.0"

func main() {
	path := flag.String("config", "gate.json", "config file, written with defaults if missing")
	flag.Parse()
	log.SetFlags(log.Ldate | log.Ltime)

	cfg, err := loadConfig(*path)
	if err != nil {
		log.Fatalf("config: %v", err)
	}
	routes := cfg.routes()
	if cfg.Secret != "" && !cfg.ProxyProtocol {
		log.Fatal("config: secret needs proxy_protocol")
	}
	log.Printf("TCGuard Gate %s: %d route(s), proxy protocol %v, signed %v", version, len(routes), cfg.ProxyProtocol, cfg.Secret != "")

	for _, route := range routes {
		g, err := newGate(cfg, route)
		if err != nil {
			log.Fatalf("config: %v", err)
		}
		listener, err := net.Listen("tcp", route.Listen)
		if err != nil {
			log.Fatalf("listen %s: %v", route.Listen, err)
		}
		log.Printf("  %s -> %s", route.Listen, route.Backend)
		go g.report(10 * time.Second)
		go g.accept(listener)
	}
	select {}
}

func (g *Gate) accept(listener net.Listener) {
	for {
		c, err := listener.Accept()
		if err != nil {
			log.Printf("accept on %s: %v", g.listen, err)
			time.Sleep(50 * time.Millisecond)
			continue
		}
		go g.serve(c)
	}
}

func (g *Gate) report(every time.Duration) {
	for range time.Tick(every) {
		g.limits.sweep()
		g.stats.mu.Lock()
		refused := g.stats.refused
		g.stats.refused = map[string]int64{}
		g.stats.mu.Unlock()
		accepted, logins, statuses := g.stats.accepted.Swap(0), g.stats.logins.Swap(0), g.stats.statuses.Swap(0)
		if accepted == 0 {
			continue
		}
		reasons := make([]string, 0, len(refused))
		for r, n := range refused {
			reasons = append(reasons, fmt.Sprintf("%s %d", r, n))
		}
		sort.Strings(reasons)
		summary := "none"
		if len(reasons) > 0 {
			summary = strings.Join(reasons, ", ")
		}
		log.Printf("[%s] connections %d, logins %d, status %d (backend %d), refused: %s",
			g.listen, accepted, logins, statuses, g.stats.backendStatus.Swap(0), summary)
	}
}
