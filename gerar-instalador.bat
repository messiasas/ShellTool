@echo off
set VERSAO=1.0.7
set UUID=4f2d856d-3f99-420a-8e1a-2c898c54020a

C:\Users\filipe.silva.PAXBR\.jdks\ms-17.0.20.1\bin\jpackage ^
  --type exe ^
  --name ShellTool ^
  --app-version %VERSAO% ^
  --vendor "Filipe" ^
  --input out\artifacts\ShellTool_jar ^
  --main-jar ShellTool.jar ^
  --main-class Main ^
  --icon icon.ico ^
  --dest dist ^
  --add-modules java.desktop,java.net.http,jdk.crypto.ec,jdk.crypto.mscapi ^
  --java-options "-Djavax.net.ssl.trustStoreType=Windows-ROOT" ^
  --win-menu --win-shortcut --win-dir-chooser ^
  --win-upgrade-uuid %UUID% 

pause