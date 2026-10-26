የሰንበት ትምህርት ቤት Attendance — Android APK project

ሁኔታ: ይህ ሙሉ Android Studio source project ነው፤ በዚህ environment ውስጥ Android SDK/Gradle ስላልተጫኑ APK binary መገንባት አልተቻለም። APK እንደተገነባ አልተገለጸም።

ባህሪያት:
- HTML/CSS/JavaScript በAndroid WebView ውስጥ ተካትቷል
- Internet permission የለውም; ዋናው attendance በoffline ይሰራል
- የተማሪ ምዝገባ, Present/Absent/Late, reports
- CSV export, JSON backup/restore (file picker ይደገፋል)
- የመረጃ ማከማቻ በዚያው app ውስጥ ነው

መገንባት:
1. Android Studio በኮምፒውተር ላይ ይጫኑ።
2. ይህን folder ከZIP ውስጥ extract ያድርጉና Android Studio > Open ይምረጡ።
3. Android SDK Platform 35 እና JDK 17 ይጫኑ (Android Studio ሊጠይቅ ይችላል)።
4. Gradle sync ከጨረሰ Build > Build Bundle(s) / APK(s) > Build APK(s) ይምረጡ።
5. APK በ app/build/outputs/apk/debug/app-debug.apk ውስጥ ይገኛል።
6. APK ወደ Android ስልክ ላክ፤ ከዚያ “Install unknown apps” ፍቃድ ሊጠየቅ ይችላል።

Login: admin / 1234 (prototype login; production-grade security አይደለም)

ማስጠንቀቂያ:
- ዳታው በዚህ ስልክ/app ላይ ብቻ ይቀመጣል። App data ከተጸዳ ወይም app ከተወገደ ሊጠፋ ይችላል፤ JSON backup በየጊዜው ያውጡ።
- QR scan አልተካተተም፤ ID ካርዶች ያለ QR ይወጣሉ።
- የመጀመሪያው password 1234 ነው፤ ለእውነተኛ የተማሪ መረጃ ከመጠቀም በፊት ደህንነትን ማጠናከር ያስፈልጋል።
