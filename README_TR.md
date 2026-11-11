# TCGuard

Minecraft sunucuları için crash, exploit ve grief koruması. 1.8'den 1.21'e.

[English](README.md)

TCGuard iki parçadan oluşur. Birini ya da ikisini birlikte kullanabilirsiniz.

| Parça | Nedir | Neyi korur |
| --- | --- | --- |
| [Eklenti](plugin/README_TR.md) | Spigot / Paper eklentisi, tek jar, bağımlılık yok | oyuncu giriş yaptıktan sonraki her paket |
| [Gate](gate/README_TR.md) | sunucunun önünde çalışan küçük program | giriş öncesi her şey ve sunucunun gizlenmesi |

```
oyuncular ──> Gate ──> BungeeCord / Velocity (isteğe bağlı) ──> Spigot ya da Paper + Eklenti
```

Eklenti en çok işi 1.8'de yapar; çünkü sunucunun kendi sınırları en az o sürümdedir. 1.9 - 1.21'de
paket biçimine bağlı olmayan denetimler çalışır. Tablo
[eklenti sayfasında](plugin/README_TR.md#hangi-sürümde-ne-çalışır).

## Hızlı kurulum

**Yalnız eklenti**
1. `TCGuard-<sürüm>.jar` dosyasını `plugins/` klasörüne atın.
2. Sunucuyu açın. Bu kadar; varsayılan ayarlar güvenlidir.

**Eklenti + Gate**
1. Yukarıdaki adımı yapın.
2. Sunucuya yalnız gate'in çalıştığı makineden ulaşılabilsin (`server-ip=127.0.0.1` ya da firewall kuralı).
3. Gate'i bir kez çalıştırın; `gate.json` oluşur. `listen` (dışarıya açık port) ve `backend` (sunucu) değerlerini yazın.
4. Aynı rastgele metni hem `secret` (gate.json) hem `proxy-protocol.secret` (eklenti config'i) alanına yazın.

Ayrıntı: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) (İngilizce).

## Ne yapmaz

Anti-cheat, bot tespiti, sohbet filtresi, giriş güvenliği, lag limiti. Büyük hacimli DDoS'u da
ememez; onu hosting firmanız ya da öndeki bir servis süzmelidir.

## Devamı

- [Kurulum rehberi](docs/DEPLOYMENT.md) — kurulum türleri, BungeeCord/Velocity, origin kilidi
- [Exploit kataloğu](docs/EXPLOITS.md) — her koruma ve onu kanıtlayan test
- [Test raporu](docs/TESTING.md)
- [Değişiklik günlüğü](CHANGELOG.md) · [Sürüm notları](docs/releases)
- [Katkı](CONTRIBUTING.md) · [Güvenlik açığı bildirme](SECURITY.md)

MIT lisanslıdır, bkz. [LICENSE](LICENSE).
