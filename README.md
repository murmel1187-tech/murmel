# MurmelsVisuals

Clientseitige Fabric-Mod: Mische Texturen aus mehreren Resource Packs ingame zu **einem** Pack.

## Benutzung
1. Lade deine Packs (z. B. von packsmc.net) und lege sie als ZIP oder Ordner in .minecraft/resourcepacks.
2. Starte Minecraft mit dieser Mod (und Fabric API) und öffne im Spiel (in einer Welt) den Chat: /murmels
3. Wähle oben ein **Basis-Pack** (liefert alles, was du nicht einzeln zuordnest).
4. Klicke bei jeder Kategorie (Crystals, Totems, Schwerter, Rüstung, ...) so lange, bis das gewünschte Pack dasteht.
   Die Reihenfolge ist: Basis, Aus (Vanilla), dann deine Packs.
5. **Anwenden** erzeugt das Pack MurmelsVisuals_Mix, aktiviert es ganz oben und lädt neu.
   Deine Original-Packs werden nicht verändert.

## Bauen (zwei getrennte Projekte)
Benötigt JDK 21 (für 1.21.11) bzw. JDK 25 (für 26.3) und Gradle 9.6.0 (oder neuer).
    cd mc-1.21.11  &&  gradle wrapper --gradle-version 9.6.0  &&  ./gradlew build
    cd mc-26.3     &&  gradle wrapper --gradle-version 9.6.0  &&  ./gradlew build
Die fertige Datei liegt danach in build/libs/ (die Datei ohne -sources). Unter Windows: gradlew.bat build.

Falls Gradle eine Version nicht findet, trage die aktuellen Werte von https://fabricmc.net/develop in
die jeweilige gradle.properties ein.

## Hinweise
- Resource-Pack-Format: 1.21.11 = 75, 26.3 = 97 (steht in VersionInfo.java der jeweiligen Version).
- Es sind keine fremden Texturen enthalten. Beachte die Lizenzen der Packs, die du mischst, und gib das gemischte Pack nicht weiter.
