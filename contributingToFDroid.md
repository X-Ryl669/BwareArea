# Contributing to F-Droid app collection

Here's the list of commands I've run to submit this app to F-Droid main repository.
Since it's not straightforward, it might help other developers to follow this *ahem* guide.

I'm using a MacOSX platform so it's not the native F-Droid format.
As far as I've understood, F-Droid is split in 2 components, the `fdroidserver` (that's used to give a front-end binary called `fdroid` in your shell) and the `fdroiddata` that is a repository where all application contributors can catalog their software, via a metadata file describing where/what is and how to build their software.

So, in order to submit your application, you first need a version of `fdroidserver` on your system.

## Fetching fdroidserver
The official solution is to clone fdroidserver's repository. However, if yo do so on a Mac, you'll find out that you need python3, pyasn1 and pyasn1-modules and pyyaml (and libyaml too). Either you install them via MacPort then pip (this is what I did, and I had to do `ln -sf /opt/bin/python3.4 fdroidserver/python3` since the python interpreter name is hardcoded), either you directly install fdroidserver from MacPorts.

The former does work, but when you run `fdroid init` command, it fails while trying to call keytool (issue #513).
The latter does work too, but it also fails further when running the keytool command for exporting your signing key.
See below for how to fix it.
So for now, the best solution to bootstrap is `sudo port install fdroidserver`

## Fetching fdroiddata
Since `fdroiddata` is huge, I highly recommand to use this command to fetch only the last commit:  `git clone --depth=1 https://gitlab.com/fdroid/fdroiddata.git`.


## Initializing your F-Droid repository 
Then you'll need to initialize a your fdroid's system in the fetched directory like this:
`cd fdroiddata`
`export ANDROID_HOME=/Users/<your user here>/Library/Android/sdk`
`fdroid init -v`   

This will fail (or not, depending on your locale, but if its does) you'll need to type this command:

`keytool -list -v -keystore keystore.jks -alias <your hostname here>.local -storepass:file .fdroid.keystorepass.txt -J-Duser.language=en` 

Then you'll need to add your project:
`fdroid import --url https://github.com/you/yourSoftware`

This, unfortunately fails for me, because the metadata stored in the current fdroiddata repository can't be read by the MacPorts fdroidserver's version. So, back to step 1, you'll need to use the last version from git like this (but don't run `fdroid init` ;-):
```
cd ..
git clone --depth=1 https://gitlab.com/fdroid/fdroidserver.git
export PATH="$PWD/fdroidserver:$PATH"
sudo port install py36-pip py36-ruamel-yaml py36-requests
sudo pip-3.6 install --upgrade pyasn1-modules pyasn1 pyyaml
cd fdroiddata
fdroid readmeta
```
Then run:
`fdroid import --url https://github.com/you/yourSoftware`

You can modify the generated file in `fdroiddata/metadata/<yourpackagename>.yml`. If you look at other packages, you'll find out that they all use `.txt` format so it might be easier to switch to this format instead (the parser is more tolerant).






