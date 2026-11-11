#!/bin/bash
cd "$(dirname "$0")"
exec ${JAVA:-java} -Xms512M -Xmx1536M -DIReallyKnowWhatIAmDoingISwear=true -Dfile.encoding=UTF-8 -jar spigot-1.8.8.jar nogui
