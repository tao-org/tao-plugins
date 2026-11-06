package ro.cs.tao.utils.executors;

import ro.cs.tao.utils.ExecutionUnitFormat;
import tools.jackson.databind.ObjectMapper;
import java.util.List;

/** Exports resolved workflow invocations using Python 3's standard library. */
public class PythonExecutor extends WorkflowScriptExecutor {

    public PythonExecutor() { }

    public PythonExecutor(ExecutionUnit unit) { super(unit); }

    @Override
    public boolean isIntendedFor(ExecutionUnitFormat format) { return format == ExecutionUnitFormat.PYTHON; }

    @Override
    protected String header() {
        return """
                #!/usr/bin/env python3
                # Generated workflow: resolved paths and Docker mounts must exist on this machine.
                import json
                import subprocess

                steps = []

                def run_workflow():
                    pending = dict(steps)
                    if len(pending) != len(steps):
                        raise RuntimeError("Duplicate workflow task")
                    completed = set()
                    while pending:
                        ready = [key for key, step in pending.items()
                                 if set(step['dependencies']) <= completed]
                        if not ready:
                            raise RuntimeError("Missing or cyclic workflow dependencies")
                        for key in ready:
                            step = pending.pop(key)
                            subprocess.run(step['command'], cwd=step['cwd'], check=True)
                            completed.add(key)
                """;
    }

    @Override
    protected String footer() { return "\nif __name__ == '__main__':\n    run_workflow()\n"; }

    @Override
    protected String step(String id, List<String> dependencies, List<String> command, String cwd) {
        ObjectMapper mapper = new ObjectMapper();
        java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("dependencies", dependencies);
        data.put("command", command);
        data.put("cwd", cwd == null || cwd.isEmpty() ? null : cwd);
        // Decode JSON so null/booleans and arbitrary Unicode remain valid Python.
        return "\nsteps.append((json.loads(" + literal(mapper.writeValueAsString(id)) + "), json.loads("
               + literal(mapper.writeValueAsString(data)) + ")))\n";
    }

    private static String literal(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "\\r").replace("\n", "\\n") + "'";
    }
}
