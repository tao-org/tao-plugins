package ro.cs.tao.utils.executors;

import ro.cs.tao.utils.ExecutionUnitFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Exports a workflow as a Bash 4+ script, ordered by task dependencies. */
public class BashExecutor extends WorkflowScriptExecutor {

    public BashExecutor() { }

    public BashExecutor(ExecutionUnit unit) { super(unit); }

    @Override
    public boolean isIntendedFor(ExecutionUnitFormat format) { return format == ExecutionUnitFormat.BASH; }

    @Override
    protected String header() {
        return """
                #!/usr/bin/env bash
                # Generated workflow: resolved paths and Docker mounts must exist on this machine.
                set -euo pipefail
                declare -a task_ids=() task_functions=() task_dependencies=()
                declare -A registered=() completed=()
                register_task() {
                    if [[ ${registered[$1]+yes} ]]; then echo "Duplicate task: $1" >&2; exit 1; fi
                    registered[$1]=1
                    task_ids+=("$1"); task_functions+=("$2"); task_dependencies+=("$3")
                }
                run_workflow() {
                    local remaining=${#task_ids[@]} progress i dep ready
                    while (( remaining > 0 )); do
                        progress=0
                        for ((i=0; i<${#task_ids[@]}; i++)); do
                            [[ ${completed[${task_ids[i]}]+yes} ]] && continue
                            ready=1
                            for dep in ${task_dependencies[i]}; do
                                if [[ ! ${completed[$dep]+yes} ]]; then ready=0; break; fi
                            done
                            (( ready )) || continue
                            "${task_functions[i]}"
                            completed[${task_ids[i]}]=1
                            remaining=$((remaining - 1)); progress=1
                        done
                        if (( ! progress )); then echo 'Missing or cyclic workflow dependencies' >&2; exit 1; fi
                    done
                }
                """;
    }

    @Override
    protected String footer() { return "\n# Execute only after all tasks have been registered.\nrun_workflow\n"; }

    @Override
    protected String step(String id, List<String> dependencies, List<String> command, String cwd) {
        String function = "task_" + UUID.randomUUID().toString().replace("-", "");
        return "\n" + function + "() (\n" + (cwd == null || cwd.isEmpty() ? "" : "  cd -- " + quote(cwd) + "\n")
               + "  " + command.stream().map(BashExecutor::quote).collect(Collectors.joining(" "))
               + "\n)\nregister_task " + quote(id) + " " + quote(function) + " " + quote(String.join(" ", dependencies)) + "\n";
    }

    static String quote(String value) { return "'" + value.replace("'", "'\"'\"'") + "'"; }
}
