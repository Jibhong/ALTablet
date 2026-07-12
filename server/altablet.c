#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdbool.h>
#include <time.h>

#ifdef __linux__
#include <unistd.h>
#include <fcntl.h>
#include <linux/uinput.h>
#endif

#ifdef _WIN32
#include <windows.h>
#endif

#include "adb_bridge.h"

#ifdef __linux__
void emit(int fd, int type, int code, int val)
{
    struct input_event ie = {0};
    ie.type = type;
    ie.code = code;
    ie.value = val;
    write(fd, &ie, sizeof(ie));
}

void send_mouse(int fd, float x, float y, bool is_down)
{
    emit(fd, EV_ABS, ABS_X, (int)x);
    emit(fd, EV_ABS, ABS_Y, (int)y);
    emit(fd, EV_KEY, BTN_LEFT, is_down);
    emit(fd, EV_SYN, SYN_REPORT, 0);
}
void clear_mouse(int fd)
{
    emit(fd, EV_KEY, BTN_LEFT, 0);
    emit(fd, EV_SYN, SYN_REPORT, 0);
}

#endif
#ifdef _WIN32
static bool win_prev_is_down = false;

void send_mouse(float x, float y, bool is_down)
{
    INPUT inputs[2] = {0};
    int count = 0;

    /* Move: Map tablet coordinates (0-32767) to Windows Absolute coords (0-65535) */
    LONG abs_x = (LONG)((x / 32767.0f) * 65535.0f);
    LONG abs_y = (LONG)((y / 32767.0f) * 65535.0f);

    inputs[count].type = INPUT_MOUSE;
    inputs[count].mi.dx = abs_x;
    inputs[count].mi.dy = abs_y;
    inputs[count].mi.dwFlags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_VIRTUALDESK;
    count++;

    /* Press: Only emit an event when the button state actually transitions */
    if (is_down != win_prev_is_down)
    {
        inputs[count].type = INPUT_MOUSE;
        inputs[count].mi.dx = abs_x;
        inputs[count].mi.dy = abs_y;
        inputs[count].mi.dwFlags = (is_down ? MOUSEEVENTF_LEFTDOWN : MOUSEEVENTF_LEFTUP) | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_VIRTUALDESK;
        count++;
        win_prev_is_down = is_down;
    }

    SendInput(count, inputs, sizeof(INPUT));
}

void clear_mouse()
{
    INPUT input = {0};
    input.type = INPUT_MOUSE;
    input.mi.dwFlags = MOUSEEVENTF_LEFTUP;
    SendInput(1, &input, sizeof(INPUT));
    win_prev_is_down = false;
}

#endif

int main(int argc, char *argv[])
{
#ifdef DEBUG
    /* Debug flags: -r = polling rate, -p = position data */
    bool dbg_rate = false;
    bool dbg_pos  = false;

    for (int i = 1; i < argc; i++)
    {
        if (strcmp(argv[i], "-r") == 0) dbg_rate = true;
        else if (strcmp(argv[i], "-p") == 0) dbg_pos = true;
        else
        {
            printf("Usage: %s [-r] [-p]\n", argv[0]);
            printf("  -r  Print polling rate (samples/sec)\n");
            printf("  -p  Print received position data\n");
            return 1;
        }
    }

    if (!dbg_rate && !dbg_pos)
    {
        printf("Debug build: no flags specified, enabling all output.\n");
        printf("  -r  Print polling rate (samples/sec)\n");
        printf("  -p  Print received position data\n");
        dbg_rate = true;
        dbg_pos  = true;
    }
#endif
#ifdef __linux__

    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0)
        fd = open("/dev/input/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0)
    {
        perror("open /dev/uinput");
        return 1;
    }

    // --- SETUP: ABSOLUTE MOUSE MODE ---
    ioctl(fd, UI_SET_EVBIT, EV_SYN);
    ioctl(fd, UI_SET_EVBIT, EV_KEY);

    // CHANGE 1: Enable Standard Mouse Buttons instead of Pen/Touch
    ioctl(fd, UI_SET_KEYBIT, BTN_LEFT);  // Left Click
    ioctl(fd, UI_SET_KEYBIT, BTN_RIGHT); // Right Click (Optional, for side button)

    // Note: BTN_TOOL_PEN and BTN_TOUCH are REMOVED so the OS sees a "Mouse"

    ioctl(fd, UI_SET_EVBIT, EV_ABS);

    // X Axis Configuration
    // IMPORTANT: Ensure current_pen.x maps to 0-32767
    struct uinput_abs_setup abs_x = {
        .code = ABS_X,
        .absinfo = {.minimum = 0, .maximum = 32767, .resolution = 40}};
    ioctl(fd, UI_ABS_SETUP, &abs_x);

    // Y Axis Configuration
    struct uinput_abs_setup abs_y = {
        .code = ABS_Y,
        .absinfo = {.minimum = 0, .maximum = 32767, .resolution = 40}};
    ioctl(fd, UI_ABS_SETUP, &abs_y);

    // CHANGE 2: Removed Pressure Setup (Mice don't have pressure)

    struct uinput_setup usetup = {0};
    strcpy(usetup.name, "Altablet Mouse Mode"); // Renamed for clarity
    usetup.id.bustype = BUS_USB;
    usetup.id.vendor = 0x1234;
    usetup.id.product = 0x5678;

    ioctl(fd, UI_DEV_SETUP, &usetup);
    ioctl(fd, UI_DEV_CREATE);

    printf("Device created as Absolute Mouse.\n");

#endif

start_adb:
    system("adb forward --remove tcp:6789");
    system("adb forward tcp:6789 tcp:6789");

    int pen_socket = adb_bridge_init("127.0.0.1", 6789);

    if (pen_socket < 0)
    {
        printf("Failed to connect. Is the Android app running and ADB forwarded?\n");
        return 1;
    }

    printf("Bridge Connected! Emulating Mouse...\n");

    PenData current_pen;

#ifdef DEBUG
    /* Performance counter: samples per second */
    int sample_count = 0;
#ifdef _WIN32
    clock_t last_print = clock();
#else
    struct timespec last_print;
    clock_gettime(CLOCK_MONOTONIC, &last_print);
#endif
#endif

    while (1)
    {
        int result = adb_bridge_receive(pen_socket, &current_pen);
        if (result == 1)
        {
#ifdef DEBUG
            sample_count++;

            if (dbg_rate)
            {
#ifdef _WIN32
                clock_t now = clock();
                double elapsed = (double)(now - last_print) / CLOCKS_PER_SEC;
#else
                struct timespec now;
                clock_gettime(CLOCK_MONOTONIC, &now);
                double elapsed = (now.tv_sec - last_print.tv_sec) + 
                                 (now.tv_nsec - last_print.tv_nsec) / 1e9;
#endif
                if (elapsed >= 1.0)
                {
                    printf("Polling rate: %d samples/sec\n", sample_count);
                    sample_count = 0;
                    last_print = now;
                }
            }

            if (dbg_pos)
            {
                printf("Pos: (%.0f, %.0f) pressure=%.2f hover=%d\n",
                       current_pen.x, current_pen.y,
                       current_pen.pressure, current_pen.is_hovering);
            }
#endif
#ifdef __linux__
            send_mouse(fd, current_pen.x, current_pen.y, current_pen.pressure > 0);
#endif
#ifdef _WIN32
            send_mouse(current_pen.x, current_pen.y, current_pen.pressure > 0);
#endif
        }
        else if (result == -1)
        {
            printf("Connection lost.\n");
#ifdef __linux__
            clear_mouse(fd);
#endif
#ifdef _WIN32
            clear_mouse();
#endif
            break;
        }
    }

    adb_bridge_close(pen_socket);
    goto start_adb;

#ifdef __linux__
    ioctl(fd, UI_DEV_DESTROY);
    close(fd);
#endif
    return 0;
}
