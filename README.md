Drive Sphero with Google Glass
================

Sphero + Google Glass mashup, drive a Sphero with Google Glass.

Move your head up and down to make the Sphero go forward or backward and turn your head to make it turn.


Mode 1:

* Pair Glass and Sphero (see below)
* Install DriveSpheroGlass apk available at https://github.com/SpheroGlass/DriveSpheroGlass/blob/master/target/DriveSpheroGlass.apk?raw=true


Mode 2:

* Pair Glass and Sphero (see below)
* Clone this project: git@github.com:SpheroGlass/DriveSpheroGlass.git
* Build and run the application in your Google Glass


Pairing your Sphero and Google Glass via bluetooth:
  
* Download settings apk from http://www.glassxe.com/2013/05/23/settings-apk-and-launcher2-apk-from-the-hacking-glass-session-at-google-io/
* Install settings apk: adb install Settings.apk
* Run settings: adb shell am start -n com.android.settings/com.android.settings.Settings (you can also run it via Launcher2.apk as explained in the link above)
