.PHONY: get-deployment-target
get-deployment-target:
ifndef platform
	$(error required variable: "platform")
endif
	@./Swift/Scripts/get-deployment-target "$(platform)"
