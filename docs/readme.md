# Making new releases

Here is the sequence of bundles you have to launch to repeat the whole DB generation process.

**IMPORTANT** Note that this needs to be run from the corresponding release tag!

1. First of all you need to publish an fat-jar artifact with `sbt publish`
2. Then launch `sbt test:console` and run commands in it; for each bundle you can choose EC2 instance type in [`src/test/scala/runBundles.scala`](src/test/scala/runBundles.scala)

1. `pick16SCandidates`
   - Recommended EC2 instance type: `r3.x4large`, it has over 100GB RAM (we need a lot for the GC, because we load _everything_ in memory; see [#47](https://github.com/ohnosequences/db.rna16s/issues/47))
   - Approximate running time: several hours
   - Command: `ohnosequences.db.rna16s.test.rna16s.pick16SCandidates(your_user.AWSUser)`.  
     It returns you the instance ID. You have to terminate it **manually**.

2. `dropRedundantAssignments`
   - Recommended EC2 instance type: `r3.large` or `m3.xlarge`
   - Approximate run time: 10-20 minutes
   - Command: `ohnosequences.db.rna16s.test.rna16s.dropRedundantAssignmentsAndGenerate(your_user.AWSUser)`.  
     It returns you the instance ID. You have to terminate it **manually**.

3. MG7 + `dropInconsistentAssignments`
   * First you need to run all the steps of the MG7 pipeline (one after another, not all at once):

     ```scala
     >  ohnosequences.db.rna16s.test.mg7.pipeline.split.deploy(your_user)
     >  ohnosequences.db.rna16s.test.mg7.pipeline.blast.deploy(your_user)
     ```

     Each loquat will offer you to subscribe to the  notifications and tell you when it finished.

   * Then run `dropInconsistentAssignments`:
     - Recommended EC2 instance type: `r3.large` or `m3.xlarge`
     - Approximate run time: 10-20 minutes
     - Command:  `ohnosequences.db.rna16s.test.rna16s.dropInconsistentAssi gnmentsAndGenerate(your_user.AWSUser)`.  
     It returns you the instance ID. You have to terminate it  **manually**.

4. `releaseData`
   - This is not a bundle, just a function that you call locally and it copies objects in S3
   - Approximate run time: ~1 minute
   - Command: `ohnosequences.db.rna16s.test.releaseData(your_user.AWSUser.profile)`
