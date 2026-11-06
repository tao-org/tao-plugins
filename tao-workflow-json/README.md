Workflow script export
======================

Select `BASH_EXPORT` (job type 3, `.sh`) or `PYTHON_EXPORT` (job type 5,
`.py`). The converters are discovered through `ExecutionDescriptorConverter`.
Run the resulting file with `bash workflow.sh` or `python3 workflow.py`.
Bash export requires Bash 4 or later. Python export uses only Python 3's standard
library. Docker tasks require the Docker CLI and access to their images.

Both exporters capture resolved commands, environment variables, mounts, container
options, memory constraints and working directories. They run tasks sequentially in
dependency order, including branches and joins, and stop after a failed invocation.
Independent branches are serialized. Missing/cyclic dependencies and duplicate task
IDs produce errors. Generation never runs the captured commands. Writes from task
exporters in the same JVM are synchronized; one export job must own each script path.

Paths and mounts retain their configured values and must exist on the machine that
runs the script. These are resolved workflow snapshots; there is no positional input
or output remapping. As in the existing workflow export pipeline, datasource
preparation is performed by TAO; the exported script contains processing tasks and
expects their input datasets to be present. The former Bash exporter incorrectly inferred workflow inputs
from root-task outputs and generated invalid assignments and incomplete sidecar maps.
Neither exporter creates `inputs.txt` or `outputs.txt`.

Arguments are preserved as individual tokens. Explicit `/bin/bash -c` payloads stay
intact, so their intentional shell expansions still occur inside that shell.
The workflow execution adapter also preserves configured shell commands as explicit
shell invocations, rather than confusing shell quoting with literal argv characters.
Python uses `subprocess.run(..., check=True)` without an implicit shell. Container
types other than Docker are rejected. Exported invocations run on the script host,
rather than connecting to the original TAO worker nodes.

Validation: `WorkflowScriptExecutorTest` covers capture, registration, safe append,
and generates fixtures. Then run `python3 src/test/python/test_workflow_scripts.py`
from this module on Linux/WSL to execute both script formats against a Docker shim.
