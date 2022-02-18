FROM golang:1.17.0

WORKDIR /usr/src/app
COPY go.mod .
COPY analytics.go .
RUN go mod tidy

RUN go build -o analytics-layer
ENTRYPOINT [ "/usr/src/app/analytics-layer" ]