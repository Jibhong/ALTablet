#ifndef ADB_BRIDGE_H
#define ADB_BRIDGE_H

typedef struct {
    float x;
    float y;
    float pressure;
    int is_hovering;
} PenData;

// Returns a socket file descriptor or -1 on failure
int adb_bridge_init(const char* host, int port);

// Blocks until a full line of data is received. Returns 1 on success, 0 on failure.
int adb_bridge_receive(int sock, PenData *out_data);

// Closes the bridge
void adb_bridge_close(int sock);

#endif
