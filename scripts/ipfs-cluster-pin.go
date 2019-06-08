package main

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"os/exec"

	shell "github.com/ipfs/go-ipfs-api"
)

type Multihash struct {
	cid string
}

func (multihash Multihash) pin() {
	out, err := exec.Command("ipfs-cluster-ctl", "pin", "add", multihash.cid).Output()
	if err != nil {
		log.Fatalf("cmd.Run() failed with %s\n", err)
	}
	fmt.Printf("%s\n", out)

}

func main() {
	//Connect to local node which is running on localhost:5001
	ipfs := shell.NewShell("localhost:5001")
	for {
		sub, err := ipfs.PubSubSubscribe("social")
		if err != nil {
			fmt.Fprintf(os.Stderr, "error: %s", err)
			os.Exit(1)
		}
		message, err := sub.Next()
		cid := string(message.Data)
		multihash := Multihash{cid}
		multihash.pin()

		// ipfs.Pin(cid)

		//Get embedded objects (if needed)
		reader, err := ipfs.Cat(cid)
		var raw map[string]interface{}
		jsonErr := json.NewDecoder(reader).Decode(&raw)
		reader.Close()
		if jsonErr != nil {
			fmt.Printf("\n[JSON Error] %s", jsonErr)
		}
		file := raw["file"]
		if file != nil {
			fileJSON := file.(map[string]interface{})
			hash := fileJSON["hash"]
			if hash != nil {
				_, _ = ipfs.Cat(hash.(string))
				multihash := Multihash{hash.(string)}
				multihash.pin()
				// ipfs.Pin(hash.(string))
			}
		}
	}
}
