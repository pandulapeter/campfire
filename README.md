# Campfire (Android)
*Explore a handpicked library of high quality song lyrics and chords.*

Campfire is a companion app for musicians and people who like to sing. It contains a small, but growing number of songs and song collections as well as useful features such as transposing the key signature, auto-scroll or the ability to create your own playlists.

The app follows the latest Material design guidelines, featuring beautiful animations and supporting both light and dark themes. 

Campfire is completely free, without any ads.

[<img src="https://play.google.com/intl/en_us/badges/images/badge_new.png" />](https://play.google.com/store/apps/details?id=com.pandulapeter.campfire)

You can find the source code of the backend project [here](https://github.com/pandulapeter/campfire-backend).

### How to build
The **master** branch should always be buildable, you just need to create three configuration files after cloning the project (as these files contain sensitive information, they are not part of the repository).

*  Copy your **google-services.json** file into the **app** folder for the Firebase configuration. This does not need to be valid, as the **debug** build type will never use the Firebase API and I don't recommend compiling the other build types.
*  In the **app** folder, duplicate the **internal.keystore** file with the name **release.keystore**.
*  In the **app** folder, duplicate the **internal.keystore.properties** file with the name **release.keystore.properties**.

### Screenshots
<img src="screenshots/01.png" width="20%" /> <img src="screenshots/02.png" width="20%" />
<img src="screenshots/03.png" width="20%" /> <img src="screenshots/04.png" width="20%" />
<img src="screenshots/05.png" width="20%" /> <img src="screenshots/06.png" width="20%" />
<img src="screenshots/07.png" width="20%" /> <img src="screenshots/08.png" width="20%" />

### Pull requests
In general I'm not accepting pull requests for this project, it is open-sourced only for educational purposes. If you find a bug or any possibility for improvements, I'd very much appreciate your feedback but I'd like to be the one fixing the issues. If you'd like to help me by adding more songs to the database, check out the backend project [here](https://github.com/pandulapeter/campfire-backend).
 
### License
This software is licensed under GNU GPL 3.0. Any derivative works must follow the same open-source license. 