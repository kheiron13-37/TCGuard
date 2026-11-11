package main

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/binary"
	"net"
	"time"
)

var proxySignature = []byte("\r\n\r\n\x00\r\nQUIT\n")

const tlvSignature = 0xE0

func proxyHeader(src, dst *net.TCPAddr, secret string, now time.Time) []byte {
	h := append([]byte(nil), proxySignature...)
	var address []byte
	switch {
	case src == nil || dst == nil:
		h = append(h, 0x20, 0x00)
	case src.IP.To4() != nil && dst.IP.To4() != nil:
		h = append(h, 0x21, 0x11)
		address = append(append(address, src.IP.To4()...), dst.IP.To4()...)
	default:
		h = append(h, 0x21, 0x21)
		address = append(append(address, src.IP.To16()...), dst.IP.To16()...)
	}
	if address != nil {
		address = binary.BigEndian.AppendUint16(address, uint16(src.Port))
		address = binary.BigEndian.AppendUint16(address, uint16(dst.Port))
	}
	body := address
	if secret != "" {
		stamp := binary.BigEndian.AppendUint64(nil, uint64(now.UnixMilli()))
		mac := hmac.New(sha256.New, []byte(secret))
		mac.Write(address)
		mac.Write(stamp)
		value := mac.Sum(stamp)
		body = append(body, tlvSignature, byte(len(value)>>8), byte(len(value)))
		body = append(body, value...)
	}
	h = binary.BigEndian.AppendUint16(h, uint16(len(body)))
	return append(h, body...)
}
