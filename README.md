# gpio-socket

GPIO control via WebSocket

## Install

    npm install -g gpio-socket

## Run

    gpio-socket

## Protocol

#### Write

`["write", pin, value]`

Set the value (1 or 0) of the given pin.

#### Subscribe

`["subscribe", pin]`

Subscribe to changes on the given pin.

Change events will be of the form:

`["change", pin, value]`

## License

MIT
