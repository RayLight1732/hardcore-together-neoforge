DOCS_REPO := https://github.com/RayLight1732/hardcore-together-docs.git

# Refresh docs/ from the hardcore-together-docs repository
.PHONY: docs
docs:
	rm -rf .docs-tmp
	git clone --depth 1 $(DOCS_REPO) .docs-tmp
	rm -rf docs
	cp -r .docs-tmp/docs docs
	rm -rf .docs-tmp
