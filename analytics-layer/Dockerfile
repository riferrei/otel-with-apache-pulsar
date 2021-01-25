FROM golang:1.15.6

ADD . /usr/src/app
WORKDIR /usr/src/app

RUN go build -o analytics-layer
ENTRYPOINT [ "/usr/src/app/analytics-layer" ]