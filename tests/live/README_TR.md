# Canlı testler

Gerçek sunucular, gerçek protokol istemcileri, gerçek saldırı paketleri. Hiçbir şey taklit değil.

[English](README.md) · Sonuçlar ve kurulum: [docs/TESTING.md](../../docs/TESTING.md)

```bash
npm install

# tam test seti, Spigot 1.8.8
./run.sh etiket ../../plugin/dist/TCGuard-<sürüm>.jar                         # yalnız eklenti
TCG_GATE=<gate dosyası> ./run.sh etiket <jar>                                 # gate arkasında
TCG_GATE=<gate dosyası> TCG_SECRET=herhangi ./run.sh etiket <jar>             # origin kilidiyle

# çekirdek test seti, 1.8 - 1.21 arası her sürüm
./core.sh <sürüm> <port> <başlatma komutu> <durdurma komutu> <sunucu klasörü> <jar> [gate dosyası]
```

| Dosya | |
| --- | --- |
| `suite.js` | tüm eklenti testleri, 1.8.8 |
| `core.js` | her sürümde çalışan testler (M01 - M09, L01 - L03) |
| `gate.js` | gate ve origin kilidi testleri |
| `h.js` | bot ve konsol yardımcıları |
| `run.sh`, `srv.sh` | 1.8.8: her grup için temiz sunucu açar, sonuçları toplar |
| `core.sh` | tek sunucu sürümü: temiz açılış, `core.js` doğrudan ve gate üzerinden |
| `probe/` | sunucu durumunu okuyup değiştirmek için yalnız testte kullanılan eklenti (1.8.8) |
| `results/` | son sürüm çalıştırmasının sonuçları |
