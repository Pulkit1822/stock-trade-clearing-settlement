# infrastructure/fluentd/Dockerfile
#
# Custom Fluentd image with the Elasticsearch output plugin pre-installed.
#
# Why not use the base fluent/fluentd image directly?
# The base image does not include fluent-plugin-elasticsearch. We must install it
# at build time so the container starts cleanly without network access at runtime.
#
# Base: fluent/fluentd:v1.16-debian-1
#   - Debian variant has gem build tools available (needed to compile es plugin).
#   - Alpine variant lacks gcc/make and causes gem native extension failures.

FROM fluent/fluentd:v1.16-debian-1

# Switch to root to install gems
USER root

# Install the Elasticsearch output plugin.
# --no-document skips rdoc/ri generation to keep the image lean.
# Pin the version for reproducibility.
RUN gem install fluent-plugin-elasticsearch --no-document --version 5.4.3

# Drop back to fluent user for runtime — principle of least privilege
USER fluent
