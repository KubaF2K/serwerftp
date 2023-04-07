# Simple FTP Server

Simple FTP server implementation written in Kotlin.

Made for a university project.

It supports multiple connections on multiple threads, both passive and active connections, both binary and ASCII transfer modes, logging in with credentials (hardcoded or from a users.txt file) or anonymous login.

## Supported FTP functions:

- USER
- PASS
- CWD
- LIST (returns a fake `ls` format file list making it kind of work with GUI FTP clients like FileZilla which expect that format)
- NLST
- PWD
- XPWD (does the same as PWD)
- QUIT
- PASV (has some bugs due to using the server's local ip instead of the public one)
- EPSV
- SYST
- FEAT
- PORT
- EPRT
- RETR
- MKD
- XMKD (does the same as MKD)
- RMD
- XRMD (does the same as RMD)
- TYPE
- STOR
- DELE

#
# Prosty serwer FTP

Implementacja prostego serwera FTP napisana w Kotlinie.

Stworzona na potrzeby projektu na studia.

Wspiera wiele jednoczesnych połączeń na wielu wątkach, połączenia pasywne i aktywne, tryby binarne i ASCII, logowanie się jako użytkownik (dane logowania sztywno w kodzie lub odczytane z pliku users.txt) lub jako anonimowy użytkownik.

## Wspierane funkcje FTP:

- USER
- PASS
- LIST (zwraca listę plików udającą format `ls` co powoduje że jakoś to działa z klientami GUI takimi jak FileZilla, które oczekują formatu `ls`)
- NLST
- PWD
- XPWD (robi to samo co PWD)
- QUIT
- PASV (ma problemy przez używanie lokalnego adresu ip serwera zamiast publicznego)
- EPSV
- SYST
- FEAT
- PORT
- EPRT
- RETR
- MKD
- XMKD (robi to samo co MKD)
- RMD
- XRMD (robi to samo co RMD)
- TYPE
- STOR
- DELE
