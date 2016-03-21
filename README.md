## Dynamic Binaural Synthesis on Mobile Android Devices
This app implements dynamic binaural synthesis on Android devices. All audio processing is done in real-time. It uses my [Razor AHRS head-tracker](https://github.com/ptrbrtz/razor-9dof-ahrs), but it also works without head-tracking: you can still use the GUI to move around sound sources of the bundled or your own audio scenes.

### Installing

Instead of building it yourself, you can install the binaries from the [Release Page](https://github.com/ptrbrtz/android-dynamic-binaural-synthesis/releases). You'll find the app in the .apk file and the data (audio material, HRTFs/HRIRs) in the .zip file.

To install the app, download and install it directly on the device. You may have to tweak the system settings on the device in order to be able to install apps directly. See [here](http://developer.android.com/distribute/tools/open-distribution.html).

To install the data, there are two ways. You could either download the .zip directly to your device as well. In this case you'll need an extra app to unzip it to the root of your SD-card. I found that the free [AndroZip](https://play.google.com/store/apps/details?id=com.agilesoftresource&hl=en) does this nice and easy. (Start the app, go to your *Downloads* folder, tap the .zip file, choose *Extract to...*, leave the folder by going up one level, hit *Extract here*)

The other option is to download and unzip the file on a computer, connect your device and put the unzipped *AndroidDynamicBinauralSynthesis* folder into the root of your SD-card (have a look [here](https://support.google.com/nexus/answer/2840804) if you don't know how to access the SD-card from your computer). 

Which ever way, make sure that in the end you have an *AndroidDynamicBinauralSynthesis* folder at the root of your SD-card and that there are three folders named *HRIRs*, *Misc* and *Scenes* inside.

### Audio scenes

If you want, have a look at the *Scenes* folder in the SD-card. You can easily modify audio scenes or even create new ones. You'll figure it out, it's easy, have a look at the .asd files. Just make sure the audio files you use are in the *44100 Hz* / *16 bit PCM* format as this is the only supported format right now. New scenes will pop up in the app automatically.

### Compiling

You can import this as an Eclipse Android Project or try to migrate it to Android Studio. You'll have to have NDK set up and maybe do some setup in the project to get running by setting some paths. You'll also have to reference the Razor AHRS Android Library Project, even if you're not using the head-tracker in the end. It can be found [here](https://github.com/ptrbrtz/razor-9dof-ahrs).
