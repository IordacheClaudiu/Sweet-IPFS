package main

import (
	"encoding/json"
	"fmt"
	"os"

	shell "github.com/ipfs/go-ipfs-api"
)

func main() {
	//Connect to local node which is running on localhost:5001
	ipfs := shell.NewShell("localhost:5001")
	for {
		sub, err := ipfs.PubSubSubscribe("calamities")
		if err != nil {
			fmt.Fprintf(os.Stderr, "error: %s", err)
			os.Exit(1)
		}
		message, err := sub.Next()
		cid := string(message.Data)
		ipfs.Pin(cid)

		//Get embedded objects (if needed)
		reader, err := ipfs.Cat(cid)
		var raw map[string]interface{}
		jsonErr := json.NewDecoder(reader).Decode(&raw)
		reader.Close()
		if jsonErr != nil {
			fmt.Printf("\n[JSON Error] %s", jsonErr)
		} else {
			fmt.Printf("\n[JSON] %s", raw)
		}
		file := raw["file"]
		if file != nil {
			fileJSON := file.(map[string]interface{})
			hash := fileJSON["hash"]
			if hash != nil {
				_, _ = ipfs.Cat(hash.(string))
				ipfs.Pin(hash.(string))
			}
		}

		//PIN the object to current node
		errPin := ipfs.Pin(cid)
		if errPin != nil {
			fmt.Fprintf(os.Stderr, "\n[Object PIN]: %s", errPin)
		}
	}
}
