# Development-only golden-output oracle for the Battery Historian Kotlin port.
#
# Build context is the repository root:
#   tools/oracle/run.sh ...
# or manually:
#   docker build -f tools/oracle/Dockerfile -t battery-archeologist-oracle:local .
#
# The harness is copied into the vendored Go module's cmd/ tree so it can
# import the upstream packages without a second go.mod or a replace directive.
# Nothing in third_party/ is modified on disk.

FROM golang:1.22-bookworm AS build

ENV CGO_ENABLED=0
ENV GOFLAGS=-trimpath

WORKDIR /src
COPY third_party/battery-historian/ /src/
COPY tools/oracle/harness/ /src/cmd/oracle/

RUN go build -o /out/oracle ./cmd/oracle \
 && go build -o /out/upstream/history-parse ./cmd/history-parse \
 && go build -o /out/upstream/checkin-parse ./cmd/checkin-parse

FROM debian:bookworm-slim

COPY --from=build /out/oracle /usr/local/bin/oracle
COPY --from=build /out/upstream/ /opt/upstream/

ENTRYPOINT ["oracle"]
