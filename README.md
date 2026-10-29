# MediCab

A friendly medicine cabinet for Android. Keep track of what's in your cabinet,
get reminded before things expire, and log doses.

- **Add medicines fast**: type a few letters to pick from 12,000+ medicines registered in South Africa, or snap the box and MediCab reads the label
- **Expiry reminders**: a daily check warns you before anything expires
- **Dose log** with "how many are left"
- **3D cabinet** of your packs
- **Export and import**: Excel, CSV, JSON backup or a printable PDF
- **Private**: no account, no cloud. Your cabinet stays on your phone

## Install

1. On your Android phone, open the [latest release](https://github.com/Pluto596Polite/MediCab/releases/latest) and download `MediCab-<version>.apk`.
2. Open the downloaded file. Android asks you to allow installs from your browser or file manager the first time. Allow it, then tap **Install**.
3. Requires Android 8.0 or newer.

### Updates

MediCab checks for a new release when it opens and offers to update. Tap **Update**, then **Install** when Android asks. Your cabinet is kept. You can turn this off or check by hand under **Settings › Updates**.

You can also use [Obtainium](https://github.com/ImranR98/Obtainium): add `https://github.com/Pluto596Polite/MediCab` and it will keep MediCab up to date.

## Privacy

See [PRIVACY.md](PRIVACY.md). In short: your cabinet never leaves your phone. Optional online lookups send only a barcode number or medicine name, and the update check sends nothing about you.

## Building

Android Studio, or from the command line with JDK 21:

```
./gradlew installDebug           # debug build on a connected phone
./gradlew testDebugUnitTest      # unit tests
```

The South African medicine list is built from the Department of Health's medicine price database with `tools/build_sa_medicines.py`.

### Releasing (maintainer)

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts` and commit.
2. Run `tools/release.sh "What's new"`. It tests, builds and signs the APK with the upload key in `keystore.properties`, tags the commit and publishes the GitHub release.

## License

MediCab is open source under the [MIT License](LICENSE). Contributions and issues are welcome.

- The fonts Fredoka and Nunito are under the SIL Open Font License (see `licenses/`).
- The South African medicine list comes from the National Department of Health's public *Database of Medicine Prices*.

Medicine information in MediCab is general information, not medical advice. Always follow the pack leaflet and your pharmacist or doctor.
