package main

import (
	"bufio"
	"errors"
	"fmt"
	"io"
	"unicode/utf8"
)

var errMalformed = errors.New("malformed packet")

func readVarInt(r io.ByteReader) (int, error) {
	value := 0
	for shift := 0; shift < 35; shift += 7 {
		b, err := r.ReadByte()
		if err != nil {
			return 0, err
		}
		value |= int(b&0x7f) << shift
		if b < 0x80 {
			return value, nil
		}
	}
	return 0, errMalformed
}

func appendVarInt(b []byte, v int) []byte {
	u := uint32(v)
	for u >= 0x80 {
		b = append(b, byte(u)|0x80)
		u >>= 7
	}
	return append(b, byte(u))
}

func readFrame(r *bufio.Reader, max int) ([]byte, error) {
	n, err := readVarInt(r)
	if err != nil {
		return nil, err
	}
	if n < 1 || n > max {
		return nil, fmt.Errorf("frame of %d bytes", n)
	}
	frame := make([]byte, n)
	_, err = io.ReadFull(r, frame)
	return frame, err
}

func frame(payload []byte) []byte {
	return append(appendVarInt(make([]byte, 0, len(payload)+5), len(payload)), payload...)
}

type packet struct {
	b   []byte
	off int
}

func (p *packet) ReadByte() (byte, error) {
	if p.off >= len(p.b) {
		return 0, errMalformed
	}
	p.off++
	return p.b[p.off-1], nil
}

func (p *packet) varInt() (int, error) { return readVarInt(p) }

func (p *packet) string(max int) (string, error) {
	n, err := p.varInt()
	if err != nil || n < 0 || n > max*4 || p.off+n > len(p.b) {
		return "", errMalformed
	}
	s := p.b[p.off : p.off+n]
	p.off += n
	if !utf8.Valid(s) || utf8.RuneCount(s) > max {
		return "", errMalformed
	}
	return string(s), nil
}

func (p *packet) skip(n int) error {
	if p.off+n > len(p.b) {
		return errMalformed
	}
	p.off += n
	return nil
}

func (p *packet) done() bool { return p.off == len(p.b) }

type handshake struct {
	protocol int
	host     string
	next     int
}

func parseHandshake(b []byte) (handshake, error) {
	p := &packet{b: b}
	var h handshake
	id, err := p.varInt()
	if err != nil || id != 0 {
		return h, errMalformed
	}
	if h.protocol, err = p.varInt(); err != nil {
		return h, err
	}
	if h.host, err = p.string(255); err != nil {
		return h, err
	}
	if err = p.skip(2); err != nil {
		return h, err
	}
	if h.next, err = p.varInt(); err != nil || !p.done() || (h.next != 1 && h.next != 2) {
		return h, errMalformed
	}
	return h, nil
}

func parseLoginStart(b []byte) (string, error) {
	p := &packet{b: b}
	if id, err := p.varInt(); err != nil || id != 0 {
		return "", errMalformed
	}
	name, err := p.string(16)
	if err != nil {
		return "", err
	}

	return name, nil
}

func handshakePacket(protocol int, host string, port uint16, next int) []byte {
	b := appendVarInt(nil, 0)
	b = appendVarInt(b, protocol)
	b = appendVarInt(b, len(host))
	b = append(b, host...)
	b = append(b, byte(port>>8), byte(port))
	return appendVarInt(b, next)
}

func stringPacket(id int, s string) []byte {
	b := appendVarInt(nil, id)
	b = appendVarInt(b, len(s))
	return append(b, s...)
}
