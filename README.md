# BwareArea

This is a Point Of Interest (POI) tracking application that works offline and can be used to display a warning when approaching any.

<a href="https://f-droid.org/packages/fr.byped.bwarearea"><img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="100"></a>

It imports iGO's comma separated value file format (it's a CSV with `Lon,Lat,Type,Speed,Dirtype,DirectionDegree` columns), like `speedcam.txt`.

Then it displays a widget on top of the screen that's showing the closest POI, its speed and type.
It's also filtering POI that are further than a set distance, and can alert when your own speed is over the limit.

It's started ever manually, or when a Bluetooth device is connected (and then, it's stopped when disconnected).

Since it's using an overlay, you can use it on offline map application that does not provide such feature, like Here WeGo(probably Registered), and so on.
Since it's not using the network, it does not consume more battery downloading/syncing the POI (it's done once), and should be low on battery usage.

Any improvement welcome. 

## Some eye candy:
![Widget](/widget.png?raw=true "Widget")

![Settings](/settings.png?raw=true "Settings")
