# TCGuard Gate

Minecraft sunucunuzun ya da proxy'nizin önünde duran küçük bir program. Oyuncu giriş yapmadan önce
olan her şeyi karşılar ve asıl sunucuyu tamamen gizleyebilir.

[English](README.md) · [Ana sayfa](../README_TR.md)

Tek dosya, bağımlılık yok, Linux ve Windows. Spigot ve Paper 1.8 - 1.21 (TCGuard eklentisiyle), BungeeCord, Waterfall,
FlameCord, Aegis ve Velocity'nin önünde çalışır.

## Ne yapar

| | |
| --- | --- |
| Ping seli | sunucu listesi ping'leri önbellekten cevaplanır; sunucuya en fazla birkaç saniyede bir sorulur |
| Sessiz bağlantılar | handshake'i bitirmeyen soketler 5 saniyede kapatılır |
| Bağlantı seli | adres başına açık bağlantı ve bağlanma hızı sınırı |
| Giriş seli | saniyede en fazla N giriş sunucuya ulaşır; fazlası "Server is busy" görür |
| Çöp trafik | bozuk handshake, geçersiz isim ve Minecraft olmayan trafik sunucuya ulaşmaz |
| Yükleme istismarı | giriş yapmış istemci saniyede belirli miktarda veri gönderebilir |
| Gerçek adres | oyuncunun adresi PROXY protocol v2 ile arkaya iletilir |
| Origin kilidi | `secret` ile sunucu yalnız gate'in imzaladığı bağlantıları kabul eder |
| Birden çok sunucu | tek gate birçok sunucunun önünde durabilir (`routes`) |

Kimin bot olduğunu tahmin etmez ve büyük hacimli DDoS'u ememez.

## Çalıştırma

1. Sisteminize uygun dosyayı release sayfasından indirin.
2. Bir kez çalıştırın: `./tcguard-gate-<sürüm>-linux-amd64` (ya da `.exe`). `gate.json` oluşur.
3. `gate.json` dosyasını düzenleyip yeniden çalıştırın.

```json
{
  "listen": ":25565",
  "backend": "127.0.0.1:25566",
  "proxy_protocol": true,
  "secret": "uzun-rastgele-bir-metin"
}
```

| Ayar | Anlamı |
| --- | --- |
| `listen` | oyuncuların bağlandığı port |
| `backend` | sunucunuz ya da proxy'niz |
| `proxy_protocol` | oyuncunun adresini iletir (açık kalsın) |
| `secret` | origin kilidi; eklentideki `proxy-protocol.secret` ile aynı değer |
| `routes` | `listen`/`backend` yerine bunların listesi; birden çok sunucu için |

Diğer ayarlar (limitler, süreler, mesajlar) olduğu gibi kalabilir; açıklamaları
[docs/DEPLOYMENT.md](../docs/DEPLOYMENT.md) içindedir.

**Önemli:** gate'in arkasındaki sunucuya internetten ulaşılamamalı, yoksa saldırgan gate'in
etrafından dolaşır. `127.0.0.1` adresine bağlayın, firewall kullanın ya da origin kilidini açın.

## Gate'in arkasında

| Arka taraf | Ayar |
| --- | --- |
| Spigot / Paper + TCGuard eklentisi | eklenti config'inde `proxy-protocol.secret` (ya da `trusted: [gate adresi]`) |
| BungeeCord / Waterfall / FlameCord / Aegis | listener'da `proxy_protocol: true` |
| Velocity | `haproxy-protocol = true` |

## Derleme

```bash
go test ./...
tools/build.sh     # Go 1.22+, dosyaları ../dist altına yazar
```
