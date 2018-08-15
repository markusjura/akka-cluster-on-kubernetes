#########################################################
# Detect CPU and Memory limits while running in a limited CGroup (e.g. docker, k8s, mesos).
#
# This script will autodetect if a cpu or memory limit is applied and sets relevant JVM flags accordingly.
# It exposes environment variables with the given limits, so application logic can act accordingly.
#
# The JVM has experimental support for detecting limits:
# https://blogs.oracle.com/java-platform-group/java-se-support-for-docker-cpu-and-memory-limits
# Sadly the solution provided by Oracle has no effect on execution contexts (relies on processors in the system)
# and does not give any hint to the running application.
#
# Most of the functions here are directly taken from:
# https://github.com/fabric8io-images/run-java-sh/blob/master/fish-pepper/run-java-sh/fp-files/run-java.sh
#
#
# Customize ---------------------------------------------
#
# JAVA_MAX_MEM_RATIO: Default=50
#   Ratio used to calculate a default maximum memory in percent.
#   E.g. the "50" value implies that 50% of the memory
#   given to the container is used as the maximum heap memory with '-Xmx'.
#   Note: the default is set to 25 if less than 300Mb is available.
#
# JAVA_INIT_MEM_RATIO: Default=<not set>
#   Ratio use to calculate a default initial heap memory, in percent.
#
# SCALA_CONCURRENT_MIN_FACTOR: Default=1 (with a resulting minimum of 2)
#   Configure the default execution context. Only applies if cpus are limited.
#   Takes the number of assigned cpus * given factor.
#   This will set scala.concurrent.context.minThreads system property
#
# SCALA_CONCURRENT_NUM_FACTOR: Default=1 (with a resulting minimum of 2)
#   Configure the default execution context. Only applies if cpus are limited.
#   Takes the number of assigned cpus * given factor.
#   This will set scala.concurrent.context.numThreads system property
#
# SCALA_CONCURRENT_MAX_FACTOR: Default=1 (with a resulting minimum of 2)
#   Configure the default execution context. Only applies if cpus are limited.
#   Takes the number of assigned cpus * given factor.
#   This will set scala.concurrent.context.maxThreads system property
#
# Exposed EnvVars ----------------------------------------
#
# CONTAINER_MAX_MEMORY: Max memory for the container (if running within a container)
# CONTAINER_CORE_LIMIT: Number of cores available for the container (if running within a container)



# Generic formula evaluation based on awk
calc() {
  local formula="$1"
  shift
  echo "$@" | awk '
    function ceil(x) {
      return x % 1 ? int(x) + 1 : x
    }
    function log2(x) {
      return log(x)/log(2)
    }
    function max2(x, y) {
      return x > y ? x : y
    }
    function round(x) {
      return int(x + 0.5)
    }
    {print '"int(${formula})"'}
  '
}

# Based on the cgroup limits, figure out the max number of cores we should utilize
core_limit() {
  local cpu_period_file="/sys/fs/cgroup/cpu/cpu.cfs_period_us"
  local cpu_quota_file="/sys/fs/cgroup/cpu/cpu.cfs_quota_us"
  if [ -r "${cpu_period_file}" ]; then
    local cpu_period="$(cat ${cpu_period_file})"

    if [ -r "${cpu_quota_file}" ]; then
      local cpu_quota="$(cat ${cpu_quota_file})"
      # cfs_quota_us == -1 --> no restrictions
      if [ ${cpu_quota:-0} -ne -1 ]; then
        echo $(calc 'ceil($1/$2)' "${cpu_quota}" "${cpu_period}")
      fi
    fi
  fi
}

# Based on the cgroup and proc limits, figure out the memory constraints
max_memory() {
  # High number which is the max limit until which memory is supposed to be
  # unbounded.
  local mem_file="/sys/fs/cgroup/memory/memory.limit_in_bytes"
  if [ -r "${mem_file}" ]; then
    local max_mem_cgroup="$(cat ${mem_file})"
    local max_mem_meminfo_kb="$(cat /proc/meminfo | awk '/MemTotal/ {print $2}')"
    local max_mem_meminfo="$(expr $max_mem_meminfo_kb \* 1024)"
    if [ ${max_mem_cgroup:-0} != -1 ] && [ ${max_mem_cgroup:-0} -lt ${max_mem_meminfo:-0} ]
    then
      echo "${max_mem_cgroup}"
    fi
  fi
}

# Check for memory options and set max heap size if needed
calc_max_memory() {
  # Check whether -Xmx is already given in JAVA_OPTIONS
  if echo "${JAVA_OPTIONS:-}" | grep -q -- "-Xmx"; then
    return
  fi

  if [ -z "${CONTAINER_MAX_MEMORY:-}" ]; then
    return
  fi

  # Check for the 'real memory size' and calculate Xmx from the ratio
  if [ -n "${JAVA_MAX_MEM_RATIO:-}" ]; then
    calc_mem_opt "${CONTAINER_MAX_MEMORY}" "${JAVA_MAX_MEM_RATIO}" "mx"
  else
    if [ "${CONTAINER_MAX_MEMORY}" -le 314572800 ]; then
      # Restore the one-fourth default heap size instead of the one-half below 300MB threshold
      # See https://docs.oracle.com/javase/8/docs/technotes/guides/vm/gctuning/parallel.html#default_heap_size
      calc_mem_opt "${CONTAINER_MAX_MEMORY}" "25" "mx"
    else
      calc_mem_opt "${CONTAINER_MAX_MEMORY}" "50" "mx"
    fi
  fi
}

# Check for memory options and set initial heap size if requested
calc_init_memory() {
  # Check whether -Xms is already given in JAVA_OPTIONS.
  if echo "${JAVA_OPTIONS:-}" | grep -q -- "-Xms"; then
    return
  fi

  # Check if value set
  if [ -z "${JAVA_INIT_MEM_RATIO:-}" ] || [ -z "${CONTAINER_MAX_MEMORY:-}" ]; then
    return
  fi

  # Calculate Xms from the ratio given
  calc_mem_opt "${CONTAINER_MAX_MEMORY}" "${JAVA_INIT_MEM_RATIO}" "ms"
}

calc_mem_opt() {
  local max_mem="$1"
  local fraction="$2"
  local mem_opt="$3"

  local val=$(calc 'round($1*$2/100/1048576)' "${max_mem}" "${fraction}")
  addJava "-X${mem_opt}${val}m"
}

c2_disabled() {
  if [ -n "${CONTAINER_MAX_MEMORY:-}" ]; then
    # Disable C2 compiler when container memory <=300MB
    if [ "${CONTAINER_MAX_MEMORY}" -le 314572800 ]; then
      echo true
      return
    fi
  fi
  echo false
}

# Replicate thread ergonomics for tiered compilation.
# This could ideally be skipped when tiered compilation is disabled.
# The algorithm is taken from:
# OpenJDK / jdk8u / jdk8u / hotspot
# src/share/vm/runtime/advancedThresholdPolicy.cpp
ci_compiler_count() {
  local core_limit="$1"
  local log_cpu=$(calc 'log2($1)' "$core_limit")
  local loglog_cpu=$(calc 'log2(max2($1,1))' "$log_cpu")
  local count=$(calc 'max2($1*$2,1)*3/2' "$log_cpu" "$loglog_cpu")
  local c1_count=$(calc 'max2($1/3,1)' "$count")
  local c2_count=$(calc 'max2($1-$2,1)' "$count" "$c1_count")
  [ $(c2_disabled) = true ] && echo "$c1_count" || echo $(calc '$1+$2' "$c1_count" "$c2_count")
}

# Compute the scala thread pool setting
scala_fj_pool_num_threads() {
  local factor="$1"
  local minimum="$2"
  echo $(calc 'max2($1*$2,$3)' "$core_limit" "$factor" "$minimum")
}

# Compute all cpu options.
# Note: the settings will only be applied, if the cpu is limited.
# If the CPU is not limited, the JVM default settings apply, which uses the system # or cores.
cpu_options() {
  local core_limit="${JAVA_CORE_LIMIT:-}"
  if [ "$core_limit" = "0" ]; then
    return
  fi

  if [ -n "${CONTAINER_CORE_LIMIT:-}" ]; then
    if [ -z ${core_limit} ]; then
      core_limit="${CONTAINER_CORE_LIMIT}"
    fi
    local scalaMin=$(scala_fj_pool_num_threads ${SCALA_CONCURRENT_MIN_FACTOR:-1} 2)
    local scalaNum=$(scala_fj_pool_num_threads ${SCALA_CONCURRENT_NUM_FACTOR:-1} 2)
    local scalaMax=$(scala_fj_pool_num_threads ${SCALA_CONCURRENT_MAX_FACTOR:-1} 2)
    addJava "-XX:ParallelGCThreads=${core_limit}"
    addJava "-XX:ConcGCThreads=${core_limit}"
    addJava "-Djava.util.concurrent.ForkJoinPool.common.parallelism=${core_limit}"
    addJava "-Dscala.concurrent.context.minThreads=${scalaMin}"
    addJava "-Dscala.concurrent.context.numThreads=${scalaNum}"
    addJava "-Dscala.concurrent.context.maxThreads=${scalaMax}"
    addJava "-XX:CICompilerCount=$(ci_compiler_count $core_limit)"
  fi
}

# make sure all container attributes are added and env vars get exposed
addContainerAttributes() {
  # Read in container limits and export the as environment variables
  local core_limit="$(core_limit)"
  if [ -n "${core_limit}" ]; then
    export CONTAINER_CORE_LIMIT="${core_limit}"
  fi

  local mem_limit="$(max_memory)"
  if [ -n "${mem_limit}" ]; then
    export CONTAINER_MAX_MEMORY="${mem_limit}"
  fi

  calc_init_memory
  calc_max_memory
  cpu_options
}

addContainerAttributes

