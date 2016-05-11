Simplified BitTorrent Protocol Implementation
Christopher Chute
May 11, 2016

D E S I G N   D E C I S I O N S
-------------------------------
This project implements a simplified BitTorrent client. I made the following
design decisions regarding the protocol. Please see
    https://wiki.theory.org/BitTorrentSpecification
for a more thorough treatment of the BitTorrent protocol.
    1. I did not implement the current choking/unchoking algorithms, but rather
        decided to use random unchoking of a maximum of four peers at a time.
    2. I did not implement a tracker or interaction with a tracker. Instead
        the peers and their listen ports are given on the command line (see
        HubbleTransferTest and BigTxtTransferTest for examples).
    3. I closely followed the BitTorrent protocol for transfering messages.
        My client uses the proper encoding of messages, although testing with
        swarms "in the wild" is future work.
    4. The core of BitTorrent transfer is implemented by my client. It chops up
        a large file into smaller pieces, connects to other peers in the swarm,
        and transfers pieces in random order to other clients at their request.
        The file is assembled out-of-order, but ends up being a lossless
        download from the swarm. I also parse .torrent files as they exist
        "in the wild."
    5. To handle non-blocking reading and writing from sockets, the client main-
        tains a separate reader thread for each peer connection. This thread
        continually reads messages from the socket and puts them on a message
        queue. This design decision was critical to performance (specifically it
        increased throughput by ~300% compared to blocking message I/O).
    6. The system is not robust to invalid command line arguments, etc. It is
        also not secure. These points remain for future work.


C O M P I L I N G   A N D   R U N N I N G
-----------------------------------------

COMPILE with the following command (with src as your current working directory):
% javac ./*.java ./util/lib/*.java ./util/bencode/*.java

TESTS can be found in this README directory, including the commands to run them.
You can run "% java BitClient -h" to print the following usage screen:
usage: java BitClient [FLAGS]* torrentFile
    -h           Usage information
    -s saveFile  Specify save location
    -p IP:port   Include this address as a peer
    -v [on|off]  Verbose on/off
    -w port      Welcome socket port number
    -x seed      Start this client as seeder
    -z slow      Run in slow motion for testing


D I R E C T O R Y   S T R U C T U R E (SRC)
-------------------------------------------
1. BitClient.java: Simplified BitTorrent client, core of client functionality.
2. BitMessage.java: Handles packing and unpacking of BitTorrent messages.
    Includes all the message types as specified by the BitTorrent protocol, and
    handles portable encoding for interacting with other BitTorrent clients.
3. BitPeer.java: Holds all state of a single peer connection, including a thread
    that continually reads messages, a queue of messages, and choking/interested
    status.
4. BitReader.java: Runnable thread that continually reads messages into a shared
    queue for later processing. Has a maximum backlog of 10 messages.
5. BitWelcomer.java: Runnable thread that continually welcomes new peer connec-
    tions and places them on a welcome queue.
6. util/
    i. bencode/ (Adapted from open-source code): Handles all encoding and
        parsing of .torrent files. This is only used in initial setup and is
        not part of the transfer protocol.
        a. BDecoder.java: Parses bencoded .torrent file into BObject[]
        b. BObject.java: Interface for a decoded metainfo object
        c. BNumber.java: A decoded number object
        d. BList.java: A decoded list object
        e. BString.java: A decoded string object
        f. BDict.java: A decoded dictionary object
    ii. lib/: Library of miscellaneous utility functions needed by the BitClient.
        a. BitLibrary.java: Utility functions such as array conversion,
            SHA1 hash encoding, writing a ByteBuffer, and getting a timestamp.
7. test/
    i. torrents/: .torrent files for testing the client
        a. big.txt.torrent
        b. hubble.jpg.torrent
        c. moby_dick.txt.torrent
        d. random.txt.torrent
    ii. downloads/: default directory for saving downloaded files.
    iii. uploads/: directory where seeder finds complete files to upload.
        a. big.txt (1.5 MB)
        b. hubble.jpg (6.8 MB)
        c. moby_dick.txt (32 KB)
        d. random.txt (590 KB)
