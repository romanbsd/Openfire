#!/bin/bash
set -Eeuo pipefail

rewire_openfire() {
  rm -rf "${OPENFIRE_DIR}/conf" "${OPENFIRE_DIR}/plugins" "${OPENFIRE_DIR}/resources/security"
  ln -s "${OPENFIRE_DATA_DIR}/conf" "${OPENFIRE_DIR}/conf"
  ln -s "${OPENFIRE_DATA_DIR}/plugins" "${OPENFIRE_DIR}/plugins"
  ln -s "${OPENFIRE_DATA_DIR}/conf/security" "${OPENFIRE_DIR}/resources/security"
}

initialize_data_dir() {
  echo "Initializing ${OPENFIRE_DATA_DIR}..."
  mkdir -p "${OPENFIRE_DATA_DIR}" "${OPENFIRE_DATA_DIR}/embedded-db"

  if [[ ! -d "${OPENFIRE_DATA_DIR}/conf" ]]; then
    cp -a "${OPENFIRE_DIR}/conf_org" "${OPENFIRE_DATA_DIR}/conf"
  fi
  if [[ ! -d "${OPENFIRE_DATA_DIR}/plugins" ]]; then
    cp -a "${OPENFIRE_DIR}/plugins_org" "${OPENFIRE_DATA_DIR}/plugins"
  fi
  if [[ ! -d "${OPENFIRE_DATA_DIR}/conf/security" ]]; then
    cp -a "${OPENFIRE_DIR}/resources/security_org" "${OPENFIRE_DATA_DIR}/conf/security"
  fi

  rm -rf "${OPENFIRE_DATA_DIR}/plugins/admin"
  ln -s "${OPENFIRE_DIR}/plugins_org/admin" "${OPENFIRE_DATA_DIR}/plugins/admin"

  if [[ ! -f "${OPENFIRE_DATA_DIR}/VERSION" ]] \
      || [[ "$(<"${OPENFIRE_DATA_DIR}/VERSION")" != "${OPENFIRE_VERSION}" ]]; then
    printf '%s' "${OPENFIRE_VERSION}" > "${OPENFIRE_DATA_DIR}/VERSION"
  fi

  chmod 0750 "${OPENFIRE_DATA_DIR}"
  chown -R "${OPENFIRE_USER}:${OPENFIRE_USER}" "${OPENFIRE_DATA_DIR}"
}

install_bundled_plugins() {
  local plugin target
  for plugin in /opt/plugins/*.jar; do
    [[ -f "${plugin}" ]] || continue
    target="${OPENFIRE_DATA_DIR}/plugins/${plugin##*/}"
    echo "Installing bundled plugin: ${plugin##*/}"
    cp -f "${plugin}" "${target}"
    chmod 0644 "${target}"
    chown "${OPENFIRE_USER}:${OPENFIRE_USER}" "${target}"
  done
}

initialize_log_dir() {
  echo "Initializing ${OPENFIRE_LOG_DIR}..."
  mkdir -p "${OPENFIRE_LOG_DIR}"
  chmod 0755 "${OPENFIRE_LOG_DIR}"
  chown -R "${OPENFIRE_USER}:${OPENFIRE_USER}" "${OPENFIRE_LOG_DIR}"
  rm -rf "${OPENFIRE_DIR}/logs"
  ln -s "${OPENFIRE_LOG_DIR}" "${OPENFIRE_DIR}/logs"
}

extra_args=()
if (( $# > 0 )) && [[ "$1" == -* ]]; then
  extra_args=("$@")
  set --
fi

initialize_data_dir
install_bundled_plugins
initialize_log_dir
rewire_openfire

export OPENFIRE_HOME="${OPENFIRE_DIR}"

if (( $# == 0 )); then
  exec su-exec "${OPENFIRE_USER}:${OPENFIRE_USER}" \
    "${OPENFIRE_DIR}/bin/openfire.sh" "${extra_args[@]}"
fi

exec "$@"
