# ALTablet
ALTablet let you use your android tablet as a graphic tablet for your computer!

# Features
|  |  Feature |
| --- | --- |
| ✅| Control your mouse with your Android device |
| ✅| Low latency input stream |
| ⏳ | Customize tablet input area |
| ⏳ | Pen pressure support |


# Installations
Grab two packages from the [release page](https://github.com/Jibhong/ALTablet/releases)
## PC Part
Run server script with **sudo**
```
sudo ./altablet_server
```
or build it your self with this command
```
cd server
gcc altablet.c adb_bridge.c -o altablet_server
```
then run it with the same command
```
sudo ./altablet_server
```
## Android Part
1. Install apk file from the [release page](https://github.com/Jibhong/ALTablet/releases) or build it your self
2. **Turn on usb debugging** in your android device
