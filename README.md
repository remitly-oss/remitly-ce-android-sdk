<img src=".github/Remitly_Horizontal_Logo_Preferred_RGB_Indigo_192x44.png" width="192" title="Remitly Logo" />

# Remitly Connected Experiences SDK for Android

Remitly Connected Experiences enables businesses to offer cross border money transfers to their customers through a simple and lightweight integration.

## Requirements

- App targeting Android 12+ ([API level](https://apilevels.com/) 31)
- Supporting minimum SDK of Android 5+ ([API level](https://apilevels.com/) 21)
- [Android Gradle Plugin](https://developer.android.com/studio/releases/gradle-plugin) 4.2.0+
- [Gradle](https://gradle.org/releases/) 6.7.1+
- [AndroidX](https://developer.android.com/jetpack/androidx/)

## Examples

The RemitlyCE SDK Examples project showcases different RemitlyCE integrations in both Kotlin and Java.

- In Android Studio, open either of:
  - `Examples/ExampleKotlinApp` (uses JDK 11 & Gradle 7.3)
  - `Examples/ExampleJavaApp` (uses JDK 1.8 & Gradle 6.7)
- See notes in the [`MainActivity.kt`](./blob/main/Examples/ExampleKotlinApp/app/src/main/java/com/example/ceproject/MainActivity.kt) or [`MainActivity.java`](./blob/main/Examples/ExampleJavaApp/app/src/main/java/com/example/remitlyceapp_java/MainActivity.java) file.

## Integration

RemitlyCE can be added to your app with just a few lines of code.

In the dependencies section of your app build.gradle add:

```java
    implementation "com.remitly:cesdk:{$version}"
```

Then in your activity add:

```kotlin
    val remitly = RemitlyCE()
    remitly.loadConfig(this)
    remitly.present()
```

Note that the activity from which RemitlyCE is launched needs to be or extend `FragmentActivity`, such as `AppCompatActivity`.

### Configuration

At minimum, RemitlyCE needs to be configured with your assigned AppID value. This can be configured in code or provided in your app's `AndroidManifest.xml` (recommended):

```xml
    <meta-data
        android:name="com.remitly.cesdk.APP_ID"
        android:value="YOUR_APP_ID_HERE" />
```

Alternatively, configuration values may be set in code:

```kotlin
        val config = RemitlyCEConfiguration.build {
            defaultSendCountry = "USA"
            defaultReceiveCountry = "PHL"
            customerEmail = "example@remitly.com"
            languageCode = "en"
        }
        remitly.loadConfig(this, config)
```

| Property              | AndroidManifest Property | Description                                                            | Type   | Required | Default Value                  |
| --------------------- | ------------------------ | ---------------------------------------------------------------------- | ------ | -------- | ------------------------------ |
| appId                 | APP_ID                   | Provided by Remitly                                                    | string | [x]      |                                |
| defaultSendCountry    | DEFAULT_SEND_COUNTRY     | 3-letter ISO country code                                              | string | [ ]      | USA                            |
| defaultReceiveCountry | DEFAULT_RECEIVE_COUNTRY  | 3-letter ISO country code                                              | string | [ ]      | PHL                            |
| customerEmail         | CUSTOMER_EMAIL           | Will prepopulate the end-user login screen                             | string | [ ]      |                                |
| languageCode          | LANGUAGE_CODE            | 2-letter ISO language code. Unsupported languages fallback to English. | string | [ ]      | `Locale.getDefault().language` |

### Methods

| Method                                                                               | Description                                                                                                                                                                                                              |
| ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| loadConfig(activity: FragmentActivity): Boolean                                      | Validates the config in AndroidManifest file and initializes the SDK. Must be called prior to presenting the Remitly UX. Returns `false` on error and calls `onError` callback.                                          |
| loadConfig(activity: FragmentActivity, userConfig: RemitlyCEConfiguration?): Boolean | Accepts a `RemitlyCEConfiguration`, validates it and the AndroidManifest file config, and initializes the SDK. Must be called prior to presenting the Remitly UX. Returns `false` on error and calls `onError` callback. |
| present(): Boolean                                                                   | Launch the Remitly UX atop your app in a full-screen modal.                                                                                                                                                              |
| dismiss(): Boolean                                                                   | An idempotent API to hide any presented Remitly UX.                                                                                                                                                                      |
| logout(): Boolean                                                                    | An idempotent API to log the user out of Remitly. This must be called whenever the user logs out of your app.                                                                                                            |

### Events

RemitlyCE provides event callback methods that can be overriden by extending the RemitlyCE class.

| Event                     | Description                                                                                     |
| ------------------------- | ----------------------------------------------------------------------------------------------- |
| onUserActivity            | Triggered frequently as the user interacts with the RemitlyCE UI.                               |
| onTransferSubmitted       | Triggered when the user successfully submits a transaction request.                             |
| onError(error: Throwable) | Called when there is an error presenting the UX or when the user encounters an error in the UI. |
