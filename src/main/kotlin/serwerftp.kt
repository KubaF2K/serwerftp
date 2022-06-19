import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat

const val controlPort = 21
const val username = "admin"
const val password = "1qazXSW@"
const val rootFolderName = "data"

lateinit var rootPath: String
lateinit var controlSocket: ServerSocket
lateinit var localIp: String
var serverRunning = true
var connectionCount = 0

fun main() {
    val rootDir = File("./$rootFolderName")
    if (!rootDir.exists())
        rootDir.mkdir()
    if (rootDir.exists() && !rootDir.isDirectory)
        println("Nie można utworzyć folderu danych!")
    rootPath = rootDir.path

    controlSocket = ServerSocket(controlPort)
    localIp = InetAddress.getLocalHost().hostAddress
    println("Serwer ($localIp) nasłuchuje port $controlPort")


    while (serverRunning) {
        val clientSocket = controlSocket.accept()
        val dataPort = controlPort + 1003 + connectionCount
        val connThread = Thread(Connection(clientSocket, dataPort))

        println("Otrzymano nowe połączenie, utworzono nowy wątek")
        connectionCount++
        connThread.start()
    }

    controlSocket.close()
    println("Zatrzymano serwer")
}

class Connection(private val clientSocket: Socket, private val dataPort: Int): Runnable {
    private var currentDir = ""
    private var quit = false

    private lateinit var controlInStream: BufferedReader
    private lateinit var controlOutWriter: PrintWriter
    private var userStatus = UserStatus.NOT_LOGGED_IN

    private var dataConnSet = false
    private lateinit var dataSocket: ServerSocket
    private lateinit var dataConn: Socket
    private lateinit var dataOutWriter: PrintWriter
    private var transferMode = TransferMode.ASCII

    enum class UserStatus {
        NOT_LOGGED_IN, NOT_AUTHENTICATED, LOGGED_IN
    }
    enum class TransferMode {
        ASCII, BINARY
    }

    private fun getDataConnPassive(port: Int) {
        dataSocket = ServerSocket(port)
        dataConn = dataSocket.accept()
        dataOutWriter = PrintWriter(dataConn.getOutputStream(), true)
        dataConnSet = true
        println("Ustanowiono połączenie pasywne")
    }

    private fun getDataConnActive(ip: String, port: Int) {
        dataConn = Socket(ip, port)
        dataOutWriter = PrintWriter(dataConn.getOutputStream(), true)
        dataConnSet = true
        println("Ustanowiono połączenie aktywne")
    }

    private fun closeDataConn() {
        if (!dataConnSet)
            return
        dataOutWriter.close()
        dataConn.close()
        if (this::dataSocket.isInitialized)
            dataSocket.close()
        println("Zamknięto połączenie")
        dataConnSet = false
    }

    private fun user(user: String) {
        if (user.lowercase() == username) {
            controlOutWriter.println("331 Poprawna nazwa użytkownika, oczekiwanie na hasło")
            userStatus = UserStatus.NOT_AUTHENTICATED
        } else if (userStatus == UserStatus.LOGGED_IN) {
            controlOutWriter.println("530 Użytkownik już zalogowany")
        } else {
            controlOutWriter.println("530 Użytkownik niezalogowany")
        }
    }

    private fun pass(pass: String) {
        if (userStatus == UserStatus.NOT_AUTHENTICATED && pass == password) {
            userStatus = UserStatus.LOGGED_IN
            controlOutWriter.println("230 Zalogowano pomyślnie")
        } else if (userStatus == UserStatus.LOGGED_IN) {
            controlOutWriter.println("530 Użytkownik już zalogowany")
        } else {
            controlOutWriter.println("530 Błędne dane użytkownika")
        }
    }

    private fun cwd(dir: String) {
        if (userStatus != UserStatus.LOGGED_IN) {
            controlOutWriter.println("530 Użytkownik niezalogowany")
            return
        }

        var currDir = currentDir

        if (dir == "..") {
            val index = currDir.lastIndexOf('/')
            currDir = if (index > 0)
                currDir.substring(0, index)
            else ""
        } else if (dir[0] == '/')
            currDir = dir
        else if (dir.isNotBlank() && dir != ".")
            currDir += "/$dir"

        val f = File("$rootPath/$currDir")
        if (f.exists() && f.isDirectory && (f.path.length >= rootPath.length)) {
            currentDir = currDir
            controlOutWriter.println("250 Zmieniono aktualny katalog na $currDir")
        } else {
            controlOutWriter.println("550 Nie zmieniono katalogu, ścieżka niedostępna")
        }
    }

    private fun list(dir: String) {
        if (userStatus != UserStatus.LOGGED_IN) {
            controlOutWriter.println("530 Użytkownik niezalogowany")
            return
        }
        if (!dataConnSet || dataConn.isClosed) {
            controlOutWriter.println("425 Nie ustanowiono połączenia")
            return
        }

        val lines = ArrayList<String>()
        var currDir = currentDir
        if (dir.isNotBlank())
            currDir += "/$dir"

        val f = File("$rootPath/$currDir")
        if (!f.exists()){
            controlOutWriter.println("550 Ścieżka niedostępna")
            return
        }

        if (f.isDirectory)
            for (file in f.listFiles()!!) {
                val dateFormat = SimpleDateFormat("MMM dd yyyy")
                val line = if (file.isDirectory) {
                    "drwxr-xr-x 1 owner group ${file.length()} ${dateFormat.format(file.lastModified())} ${file.name}"
                } else ("-rw-r--r-- 1 owner group ${file.length()} ${dateFormat.format(file.lastModified())} ${file.name}")
                lines.add(line)
            }
        else {
            val dateFormat = SimpleDateFormat("MMM dd yyyy")
            val line = if (f.isDirectory) {
                "drwxr-xr-x 1 owner group ${f.length()} ${dateFormat.format(f.lastModified())} ${f.name}"
            } else ("-rw-r--r-- 1 owner group ${f.length()} ${dateFormat.format(f.lastModified())} ${f.name}")
            lines.add(line)
        }

        controlOutWriter.println("125 Otwieranie połączenia w trybie ASCII do przesłania listy plików")
        for (filename in lines) {
            dataOutWriter.println(filename)
        }
        controlOutWriter.println("226 Zakończono transfer")
        closeDataConn()
    }

    private fun nlst(dir: String) {
        if (userStatus != UserStatus.LOGGED_IN) {
            controlOutWriter.println("530 Użytkownik niezalogowany")
            return
        }

        if (!dataConnSet || dataConn.isClosed) {
            controlOutWriter.println("425 Nie ustanowiono połączenia")
            return
        }

        val list: Array<String>?

        var currDir = currentDir
        if (dir.isNotBlank())
            currDir += "/$dir"

        val f = File("$rootPath/$currDir")
        list =
            if (f.exists() && f.isDirectory)
                f.list()
            else if (f.exists() && f.isFile) Array(1) {f.name}
            else null

        if (list.isNullOrEmpty()) {
            controlOutWriter.println("550 Ścieżka niedostępna")
            return
        }

        controlOutWriter.println("125 Otwieranie połączenia w trybie ASCII do przesłania listy plików")
        for (filename in list) {
            dataOutWriter.println(filename)
        }
        controlOutWriter.println("226 Zakończono transfer")
        closeDataConn()
    }

    private fun port(args: String) {
        val splitArgs = args.split(",")
        if (splitArgs.count() != 6) {
            controlOutWriter.println("501 Błędne dane")
        }
        val ip = "${splitArgs[0]}.${splitArgs[1]}.${splitArgs[2]}.${splitArgs[3]}"
        val port = splitArgs[4].toInt()*256 + splitArgs[5].toInt()

        getDataConnActive(ip, port)
        controlOutWriter.println("200 OK")
    }

    private fun eprt(args: String) {
        val splitArgs = args.split("\\|")
        for (arg in splitArgs) println(arg)
        val ipVersion = splitArgs[1]
        val ip = splitArgs[2]
        if (ipVersion != "1" && ipVersion != "2") {
            controlOutWriter.println("501 Niewspierana wersja IP")
            return
        }
        val port = splitArgs[3].toInt()

        getDataConnActive(ip, port)
        controlOutWriter.println("200 OK")
    }

    private fun pwd() {
        controlOutWriter.println("257 \"/$currentDir\"")
    }

    private fun pasv() {
        val splitIp = localIp.split('.')
        val p1 = dataPort / 256
        val p2 = dataPort % 256

        controlOutWriter.println("227 Otwieranie połączenia pasywnego (${splitIp[0]},${splitIp[1]},${splitIp[2]},${splitIp[3]},$p1,$p2)")
        getDataConnPassive(dataPort)
    }

    private fun epsv() {
        controlOutWriter.println("229 Rozszerzone otwieranie połączenia pasywnego (|||$dataPort|)")
        getDataConnPassive(dataPort)
    }

    private fun quit() {
        controlOutWriter.println("221 Zamykanie połączenia")
        quit = true
    }

    private fun syst() {
        controlOutWriter.println("215 Jakiś serwer")
    }

    private fun feat() {
        controlOutWriter.println("211-Wspierane rozszerzenia:")
        controlOutWriter.println("211 END")
    }

    private fun mkd(dir: String) {
        if (userStatus != UserStatus.LOGGED_IN) {
            controlOutWriter.println("530 Użytkownik niezalogowany")
            return
        }

        if (dir.isBlank() || !dir.matches(Regex("^[a-zA-Z\\d ]+$"))) {
            controlOutWriter.println("550 Błędna nazwa katalogu")
            return
        }

        val fdir = File("$rootPath/$currentDir/$dir")
        if (!fdir.mkdir()) {
            controlOutWriter.println("550 Nie udało się stworzyć nowego katalogu")
            println("Nie udało się stworzyć katalogu")
            return
        }

        controlOutWriter.println("250 Stworzono katalog")
    }

    private fun rmd(dir: String) {
        if (userStatus != UserStatus.LOGGED_IN) {
            controlOutWriter.println("530 Użytkownik niezalogowany")
            return
        }

        if (dir.isBlank() || !dir.matches(Regex("^[a-zA-Z\\d ]+$"))) {
            controlOutWriter.println("550 Błędna nazwa katalogu")
            return
        }

        val fdir = File("$rootPath/$currentDir/$dir")
        if (!fdir.exists() || !fdir.isDirectory) {
            controlOutWriter.println("550 Niepoprawna ścieżka")
            return
        }

        fdir.delete()
        controlOutWriter.println("250 Usunięto katalog")
    }

    private fun type(args: String) {
        when (args.uppercase()) {
            "A" -> {
                transferMode = TransferMode.ASCII
                controlOutWriter.println("200 OK")
            }
            "I" -> {
                transferMode = TransferMode.BINARY
                controlOutWriter.println("200 OK")
            }
            else -> controlOutWriter.println("504 Nie OK")
        }
    }

    private fun retr(file: String) {
        if (userStatus != UserStatus.LOGGED_IN) {
            controlOutWriter.println("530 Użytkownik niezalogowany")
            return
        }

        val f = File("$rootPath/$currentDir/$file")
        if (!f.exists()) {
            controlOutWriter.println("550 Plik nie istnieje")
            return
        }

        when (transferMode) {
            TransferMode.BINARY -> {
                controlOutWriter.println("150 Otwieranie połączenia w trybie binarnym dla pliku ${f.name}")
                val outStream = BufferedOutputStream(dataConn.getOutputStream())
                val inStream = BufferedInputStream(FileInputStream(f))

                println("Rozpoczynanie przesyłania: ${f.name}")

                val buffer = ByteArray(1024) {0}
                while (true) {
                    val l = inStream.read(buffer, 0, 1024)
                    if (l == -1) break

                    outStream.write(buffer, 0, l)
                }

                inStream.close()
                outStream.close()

                println("Zakończono przesyłanie pliku: ${f.name}")
                controlOutWriter.println("226 Zakończono przesyłanie pliku. Zamykanie połączenia")
            }
            TransferMode.ASCII -> {
                controlOutWriter.println("150 Otwieranie połączenia w trybie ASCII dla pliku ${f.name}")
                val outWriter = PrintWriter(dataConn.getOutputStream(), true)
                val inReader = BufferedReader(FileReader(f))

                println("Rozpoczynanie przesyłania: ${f.name}")

                while (true) {
                    val line = inReader.readLine() ?: break
                    outWriter.println(line)
                }

                inReader.close()
                outWriter.close()

                println("Zakończono przesyłanie pliku: ${f.name}")
                controlOutWriter.println("226 Zakończono przesyłanie pliku. Zamykanie połączenia")
            }
        }
        closeDataConn()
    }

    private fun dele(filename: String) {
        if (userStatus != UserStatus.LOGGED_IN) {
            controlOutWriter.println("530 Użytkownik niezalogowany")
            return
        }

        val f = File("$rootPath/$currentDir/$filename")
        if (!f.exists()) {
            controlOutWriter.println("550 Plik nie istnieje")
            return
        }

        if (f.delete())
            controlOutWriter.println("250 Usunięto plik")
        else
            controlOutWriter.println("550 Nie można usunąć pliku")
    }

    private fun stor(filename: String) {
        if (userStatus != UserStatus.LOGGED_IN) {
            controlOutWriter.println("530 Użytkownik niezalogowany")
            return
        }

        if (filename.isBlank()) {
            controlOutWriter.println("501 Nie podano nazwy pliku")
            return
        }

        val f = File("$rootPath/$filename")
        if (f.exists()) {
            controlOutWriter.println("550 Plik już istnieje")
            return
        }

        when (transferMode) {
            TransferMode.BINARY -> {
                controlOutWriter.println("150 Otwieranie połączenia w trybie binarnym dla pliku ${f.name}")
                val outStream = BufferedOutputStream(FileOutputStream(f))
                val inStream = BufferedInputStream(dataConn.getInputStream())

                println("Otrzymywanie pliku ${f.name}")

                val buffer = ByteArray(1024) {0}

                while (true) {
                    val l = inStream.read(buffer, 0, 1024)
                    if (l == -1) break

                    outStream.write(buffer, 0, l)
                }

                inStream.close()
                outStream.close()

                println("Zakończono otrzymywanie pliku: ${f.name}")
                controlOutWriter.println("226 Zakończono przesyłanie pliku. Zamykanie połączenia")
            }
            TransferMode.ASCII -> {
                controlOutWriter.println("150 Otwieranie połączenia w trybie ASCII dla pliku ${f.name}")
                val outWriter = PrintWriter(FileOutputStream(f), true)
                val inReader = BufferedReader(InputStreamReader(dataConn.getInputStream()))

                println("Otrzymywanie pliku ${f.name}")

                while (true) {
                    val line = inReader.readLine() ?: break
                    outWriter.println(line)
                }

                inReader.close()
                outWriter.close()

                println("Zakończono otrzymywanie pliku: ${f.name}")
                controlOutWriter.println("226 Zakończono przesyłanie pliku. Zamykanie połączenia")
            }
        }
        closeDataConn()
    }

    override fun run() {
        controlInStream = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
        controlOutWriter = PrintWriter(clientSocket.getOutputStream(), true)

        controlOutWriter.println("220 Połączono z serwerem")

        while (!quit) {
            val commandLine = controlInStream.readLine() ?: continue
            val index = commandLine.indexOf(' ')
            val command = if (index == -1) commandLine.uppercase() else commandLine.substring(0, index).uppercase()
            val args = if (index == -1) "" else commandLine.substring(index+1)

            println("Wywołane polecenie: $command $args")

            when (command) {
                "USER" -> user(args)
                "PASS" -> pass(args)
                "CWD" -> cwd(args)
                "LIST" -> list(args)
                "NLST" -> nlst(args)
                "PWD" -> pwd()
                "XPWD" -> pwd()
                "QUIT" -> quit()
                "PASV" -> pasv()
                "EPSV" -> epsv()
                "SYST" -> syst()
                "FEAT" -> feat()
                "PORT" -> port(args)
                "EPRT" -> eprt(args)
                "RETR" -> retr(args)
                "MKD" -> mkd(args)
                "XMKD" -> mkd(args)
                "RMD" -> rmd(args)
                "XRMD" -> rmd(args)
                "TYPE" -> type(args)
                "STOR" -> stor(args)
                "DELE" -> dele(args)
                else -> controlOutWriter.println("501 Nieznane polecenie")
            }
        }
        controlInStream.close()
        controlOutWriter.close()
        println("Zamknięte gniazda")
    }

}