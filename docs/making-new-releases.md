# Making new releases

Here is the sequence of bundles you have to launch to repeat the whole DB generation process.

**IMPORTANT** Note that this needs to be run from the corresponding release tag!

1. First of all you need to publish an fat-jar artifact with `sbt publish`
2. Then launch `sbt test:console` and run commands in it; for each bundle you can choose EC2 instance type in [`src/test/scala/runBundles.scala`](src/test/scala/runBundles.scala)

> **NOTE** To make it a bit shorter I assume that you first do `import ohnosequences.db._` in the `test:console`

1. `pick16SCandidates`
  - Recommended EC2 instance type: `r3.x2large`, it has 60GB RAM
  - Approximate running time: several hours
  - Command: `test.rna16s.launch(rna16s.compats.pick16SCandidates, your_aws_user)`.  
    It returns you the instance ID. You have to terminate it **manually**.

2. `dropRedundantAssignments`
  - Recommended EC2 instance type: `r3.large` or `m3.xlarge`
  - Approximate run time: 10-20 minutes
  - Command: `test.rna16s.launch(rna16s.compats.dropRedundantAssignmentsAndGenerate, your_aws_user)`.  
    It returns you the instance ID. You have to terminate it **manually**.

3. MG7 + `dropInconsistentAssignments`
  1. First you need to run all the steps of the MG7 pipeline (one after another, not all at once):
    ```scala
    > rna16s.referenceDBPipeline.split.deploy(your_aws_user)
    > rna16s.referenceDBPipeline.blastLoquat.deploy(your_aws_user)
    > rna16s.referenceDBPipeline.assignLoquat.deploy(your_aws_user)
    > rna16s.referenceDBPipeline.mergeLoquat.deploy(your_aws_user)
    ```
    Each loquat will offer you to subscribe to the notifications and tell you when it finished.

  2. Then run `dropInconsistentAssignments`:
    - Recommended EC2 instance type: `r3.large` or `m3.xlarge`
    - Approximate run time: 10-20 minutes
    - Command: `test.rna16s.launch(rna16s.compats.filter3AndGenerate, your_aws_user)`.  

    It returns you the instance ID. You have to terminate it **manually**.
