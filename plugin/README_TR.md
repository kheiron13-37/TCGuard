# TCGuard Eklentisi

1.8'den 1.21'e Spigot / Paper eklentisi. Tek jar, bağımlılık yok. Oyuncuların giriş sonrası
gönderdiklerini sunucu çözmeden önce denetler.

[English](README.md) · [Ana sayfa](../README_TR.md)

## Gereksinimler

- Spigot, Paper ya da bir fork'u, 1.8 - 1.21
- Java 8 ya da üstü (sunucunuz hangisiyle çalışıyorsa)

ProtocolLib artık gerekmiyor.

## Hangi sürümde ne çalışır

| Koruma | 1.8 | 1.9 - 1.21 |
| --- | --- | --- |
| Aşırı büyük paket ve sıkıştırma bombası, çözülmeden reddedilir | var | var |
| Paket seli | var, paket türüne göre ağırlıklı | var, tüm paketler birlikte sayılır |
| Sohbet ve komutta Log4Shell, log filtresi | var | var |
| Eklenti exploit komutları (`//calc`, ...) | var | var |
| Op listesi (force-OP) | var | var |
| [Gate](../gate/README_TR.md) için PROXY protocol ve origin kilidi | var | var |
| Paket içeriği: NBT bombası, kitap sayfası, tabela satırı, geçersiz tıklama | var | yok |
| Hileli creative eşyalar, aşırı büyülü eşyalar | var | yok |
| Kova, dispenser ya da mantarla end portalı silme | var | yok |

Son üç satır 1.8 paket biçimini okur ve 1.8 davranışını yamalar. Yeni sürümlerde biçim neredeyse
her güncellemede değişti ve sunucular kendi sınırlarını getirdi; TCGuard o denetimleri orada
tekrarlamaz. Eklenti açılırken hangi setin etkin olduğunu loga yazar.

## Test edilenler

| Sunucu | Java | Sonuç |
| --- | --- | --- |
| Spigot / TacoSpigot 1.8.8 | 25 | tam test seti |
| Paper 1.12.2 | 8 | çekirdek test seti |
| Paper 1.16.5 | 8 | çekirdek test seti |
| Paper 1.20.4 | 17 | çekirdek test seti |
| Paper 1.21.8 | 25 | çekirdek test seti |

Aradaki sürümler komşularıyla aynı kod yolunu kullanır ama çalıştırılmadı. Folia desteklenmez.
Sayılar ve ayrıntı: [docs/TESTING.md](../docs/TESTING.md).

## Kurulum

Jar'ı `plugins/` klasörüne atıp sunucuyu açın. `plugins/TCGuard/config.yml` ilk açılışta oluşur.
5.x'ten geçiyorsanız: başka eklenti kullanmıyorsa ProtocolLib'i kaldırın; config geçerli kalır.
4.x'ten geçiyorsanız: önce eski `plugins/TCGuard` klasörünü silin.

## Neye karşı korur

| Alan | Örnekler |
| --- | --- |
| Zararlı paketler, sunucu çözmeden önce | aşırı büyük paket, sıkıştırma bombası; 1.8'de ayrıca NBT bombası, dev kitap sayfası ve tabela satırı |
| Geçersiz paketler (1.8) | imkânsız pencere tıklaması, NaN araç girdisi, tabela ve kitapta translate bileşeni |
| Paket seli | fazla paketler düşürülür, sonra oyuncu atılır |
| Hileli eşyalar (1.8) | creative eşyalar vanilla özelliklerinden yeniden kurulur; 32k büyüler sıfırlanır |
| Log4Shell | sohbette ya da komutta `${jndi:...}` kick sebebidir ve loga hiç düşmez |
| Eklenti exploit komutları | WorldEdit `//calc`, HolographicDisplays `readtext`, Multiverse regex, PEX |
| End portalları (1.8) | kova, dispenser ya da büyüyen mantarla silinemez |
| Force-OP | isteğe bağlı izinli op listesi |
| Gate'i atlayan doğrudan bağlantılar | origin kilidi, bkz. [gate](../gate/README_TR.md) |

Testleriyle tam liste: [docs/EXPLOITS.md](../docs/EXPLOITS.md).

## Komutlar

| Komut | Ne yapar |
| --- | --- |
| `/tcguard status` | sürüm, korunan bağlantı ve engelleme sayısı |
| `/tcguard reload` | config'i yeniden okur |
| `/tcguard vl <oyuncu>` | oyuncunun güncel ihlal puanları |
| `/tcguard alerts` | uyarıları kendiniz için açar/kapatır |

## İzinler

| İzin | Varsayılan | |
| --- | --- | --- |
| `tcguard.admin` | op | `/tcguard` kullanımı |
| `tcguard.alerts` | op | uyarıları görme |
| `tcguard.bypass` | kimse | kontrolleri atlama (oyuncu girerken okunur) |

## Ayarlar

Her ayar [`resources/config.yml`](resources/config.yml) içinde açıklanmıştır. En çok dokunacaklarınız:

| Ayar | Anlamı |
| --- | --- |
| `violations.packets.weights` | modlu istemci takılıyorsa ilgili değeri düşürün (1.9+ için `OTHER` kullanılır) |
| `frames.modern-max-bytes` | 1.9+ sunucunun kabul edeceği en büyük paket (varsayılan 1 MB) |
| `items.max-enchant-level` | 1.8: bunun üstündeki büyüler sıfırlanır (varsayılan 10) |
| `commands.blocked` | engellenecek exploit komutları |
| `op-guard.allowed` | op olabilecek isimler; boş = kapalı |
| `proxy-protocol.secret` | gate'teki `secret` ile aynı değer; origin kilidini açar |

## Derleme

```bash
mvn package            # ya da Maven olmadan:
tools/build.sh         # libs/spigot-1.8.8.jar gerekir
tools/test.sh          # birim testler ve fuzzer
```

Eklenti bir kez 1.8.8 API'sine karşı derlenir, yeni sunucularda ihtiyacı olanı çalışırken bulur;
bu yüzden her sürüm için tek jar vardır.
