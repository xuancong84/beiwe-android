## Compiling and running the Beiwe Android app

### Compiling
To compile and sign the app, you must add a directory called `private` (`beiwe-android`'s `.gitignore` file keeps the `private` directory out of Git), and a file called `private/keystore.properties`, with these lines (no quotes around the values that you fill in):
```
storePassword=KEYSTORE_PASSWORD
keyPassword=KEY_PASSWORD
keyAlias=KEY_ALIAS
storeFile=KEYSTORE_FILEPATH
```

You can also configure a Sentry DSN for each build type in this file.
```
releaseDSN=https://publicKey:secretKey@host:port/1?options
betaDSN=https://publicKey:secretKey@host:port/1?options
developmentDSN=https://publicKey:secretKey@host:port/1?options
```

### Three Build Variants
There are three Build Variants of the Android app, specified in the `buildTypes` section of `app/build.gradle`.  To select which Build Variant the app compiles as, go to **Build** > **Select Build Variant** in the menu bar [(see the documentation)](https://developer.android.com/studio/run/index.html#changing-variant).  The three build types are:

* **release**- points to the production server (studies.beiwe.org) and doesn't have the Debug Interface.  The app is named "Beiwe" when installed on the phone.

* **beta**- points to the staging server (staging.beiwe.org), has the Debug Interface, and allows passwords as short as 1 character.  The app is named "Beiwe-beta" when installed on the phone.

* **development**- points to the staging server (staging.beiwe.org), has the Debug Interface, and allows passwords as short as 1 character.  The Debug Interface also has some extra buttons that are only useful for developers, like buttons to crash the app.  Also includes some extra logging statements (that are printed to Android Monitor if the phone is plugged into a debugger, but are not printed to Beiwe log files).  The app is named "Beiwe-development" when installed on the phone.
