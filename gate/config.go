package main

import (
	"encoding/json"
	"os"
)

type Route struct {
	Listen  string `json:"listen"`
	Backend string `json:"backend"`
}

type Config struct {
	Listen  string `json:"listen"`
	Backend string `json:"backend"`

	Routes        []Route `json:"routes"`
	ProxyProtocol bool    `json:"proxy_protocol"`

	Secret string `json:"secret"`

	HandshakeTimeout int `json:"handshake_timeout_seconds"`
	IdleTimeout      int `json:"idle_timeout_seconds"`
	MaxPending       int `json:"max_pending_handshakes"`

	PerIP struct {
		Connections int     `json:"connections"`
		Rate        float64 `json:"connects_per_second"`
		Burst       float64 `json:"burst"`
	} `json:"per_ip"`

	LoginsPerSecond float64 `json:"logins_per_second"`
	NamePattern     string  `json:"name_pattern"`
	StatusCache     int     `json:"status_cache_seconds"`

	Upload struct {
		BytesPerSecond float64 `json:"bytes_per_second"`
		Burst          float64 `json:"burst"`
	} `json:"upload"`

	Messages struct {
		Busy        string `json:"busy"`
		Unavailable string `json:"unavailable"`
	} `json:"messages"`
}

func defaults() *Config {
	c := &Config{
		Listen:           ":25565",
		Backend:          "127.0.0.1:25566",
		ProxyProtocol:    true,
		HandshakeTimeout: 5,
		IdleTimeout:      30,
		MaxPending:       512,
		LoginsPerSecond:  20,
		NamePattern:      "^[A-Za-z0-9_]{1,16}$",
		StatusCache:      3,
	}
	c.PerIP.Connections = 5
	c.PerIP.Rate = 2
	c.PerIP.Burst = 10
	c.Upload.BytesPerSecond = 256 << 10
	c.Upload.Burst = 1 << 20
	c.Messages.Busy = "Server is busy, try again in a few seconds."
	c.Messages.Unavailable = "Server is restarting."
	return c
}

func loadConfig(path string) (*Config, error) {
	c := defaults()
	data, err := os.ReadFile(path)
	if err == nil {
		err = json.Unmarshal(data, c)
	} else if os.IsNotExist(err) {
		err = nil
	}
	if err != nil {
		return nil, err
	}
	out, _ := json.MarshalIndent(c, "", "  ")
	return c, os.WriteFile(path, append(out, '\n'), 0o644)
}

func (c *Config) routes() []Route {
	if len(c.Routes) > 0 {
		return c.Routes
	}
	return []Route{{Listen: c.Listen, Backend: c.Backend}}
}
