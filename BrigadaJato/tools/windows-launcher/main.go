// Lancador do Brigada Jato para Windows: abre o jogo com o Java que vem junto (pasta jre).
// Compilar no Linux: GOOS=windows GOARCH=amd64 go build -ldflags "-H=windowsgui -s -w" -o BrigadaJato.exe
package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"syscall"
	"unsafe"
)

// msg mostra uma caixa de aviso do Windows.
func msg(title, text string) {
	user32 := syscall.NewLazyDLL("user32.dll")
	box := user32.NewProc("MessageBoxW")
	t, _ := syscall.UTF16PtrFromString(title)
	m, _ := syscall.UTF16PtrFromString(text)
	box.Call(0, uintptr(unsafe.Pointer(m)), uintptr(unsafe.Pointer(t)), 0x10)
}

func main() {
	exe, err := os.Executable()
	if err != nil {
		msg("Brigada Jato", "Nao achei a pasta do jogo.")
		return
	}
	dir := filepath.Dir(exe)
	javaw := filepath.Join(dir, "jre", "bin", "javaw.exe")
	jar := filepath.Join(dir, "jogo", "BrigadaJato.jar")
	for _, f := range []string{javaw, jar} {
		if _, err := os.Stat(f); err != nil {
			msg("Brigada Jato", "Arquivo faltando:\n"+f+"\n\nExtraia o ZIP inteiro antes de abrir o jogo.")
			return
		}
	}
	// a saida do Java vai para um arquivo de log; se o jogo fechar com erro, mostra o final dele
	logPath := filepath.Join(os.TempDir(), "BrigadaJato-log.txt")
	logFile, _ := os.Create(logPath)
	args := []string{"-Xms128m", "-Xmx1g", "-jar", jar}
	args = append(args, os.Args[1:]...)
	cmd := exec.Command(javaw, args...)
	cmd.Dir = dir
	if logFile != nil {
		cmd.Stdout = logFile
		cmd.Stderr = logFile
	}
	if err := cmd.Run(); err != nil {
		if logFile != nil {
			logFile.Close()
		}
		data, _ := os.ReadFile(logPath)
		text := string(data)
		if len(text) > 1500 {
			text = text[len(text)-1500:]
		}
		if strings.TrimSpace(text) == "" {
			text = err.Error()
		}
		msg("Brigada Jato - erro", "O jogo fechou com erro.\nDetalhes em: "+logPath+"\n\n"+text)
	}
}
