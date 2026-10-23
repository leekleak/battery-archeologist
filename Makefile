SHELL := /bin/bash
.DEFAULT_GOAL := help

help:
	@echo "Battery Archeologist"
	@echo "  make ready      boot emulator, install/start Shizuku, install app, seed permission, launch"
	@echo "  make build      assemble debug APK"
	@echo "  make deploy     install + launch the app"
	@echo "  make test       core tests + assemble"
	@echo "  make emulator   start the headless emulator"
	@echo "  make shizuku    install/start/verify Shizuku"
	@echo "  make status     emulator + Shizuku status"
	@echo "  make layout     dump the current UI tree"
	@echo "  make shot       screenshot, downscaled"
	@echo "  make battery    LEVEL=50 MODE=discharging"
	@echo "  make logs       recent logcat"
	@echo "  make smoke      end-to-end health check"
	@echo "  make oracle     re-capture the API 36 fixture and regenerate golden outputs"

build:
	source scripts/env.sh && ./gradlew :app:assembleDebug --console=plain

test:
	source scripts/env.sh && ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain

emulator:
	./scripts/emulator.sh start

shizuku:
	./scripts/shizuku.sh install
	./scripts/shizuku.sh start
	./scripts/shizuku.sh status

ready:
	./scripts/ready.sh

status:
	./scripts/emulator.sh status
	./scripts/shizuku.sh status

layout:
	./scripts/agent-ui.sh layout

shot:
	./scripts/agent-ui.sh shot

logs:
	./scripts/agent-ui.sh logs

smoke:
	./scripts/smoke.sh

oracle:
	./scripts/capture-fixture.sh api36 --force --oracle

battery:
	./scripts/emulator.sh battery $(or $(LEVEL),50) $(or $(MODE),discharging)

.PHONY: help build test emulator shizuku ready status layout shot logs smoke oracle battery
