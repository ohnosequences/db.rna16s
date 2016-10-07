# Making new releases

Here is the sequence of bundles you have to launch to repeat the whole DB generation process.

**IMPORTANT** Note that this needs to be run from the corresponding release tag!

1. First of all you need to publish an fat-jar artifact with `sbt publish`
2. Then launch `sbt test:console` and run commands in it; for each bundle you can choose EC2 instance type in [`src/test/scala/runBundles.scala`](src/test/scala/runBundles.scala)

3. `pick16SCandidates`
   - Recommended EC2 instance type: `r3.x4large`, it has over 100GB RAM (we need a lot for the GC, because we load _everything_ in memory; see [#47](https://github.com/ohnosequences/db.rna16s/issues/47))
   - Approximate running time: ~30 minutes
   - Command:

      ```scala
      ohnosequences.db.rna16s.test.rna16s.pick16SCandidates(your_user.AWSUser)
      ```
      It returns you the instance ID. You have to terminate it **manually**.

4. `dropRedundantAssignments`
   - Recommended EC2 instance type: `r3.large` or `m3.xlarge`
   - Approximate run time: ~5 minutes
   - Command:

     ```scala
     ohnosequences.db.rna16s.test.rna16s.dropRedundantAssignmentsAndGenerate(your_user.AWSUser)
     ```
     It returns you the instance ID. You have to terminate it **manually**.

5. MG7:
   - First run split step
      ```scala
      ohnosequences.db.rna16s.test.mg7.pipeline.split.deploy(your_user)
      ```

   - Then wait for it to finish and run BLAST
      ```scala
      ohnosequences.db.rna16s.test.mg7.pipeline.blast.deploy(your_user)
      ```

      Each loquat will offer you to subscribe to the notifications and tell you when it finished. They will terminate on success automatically.

6. `clusterSequences`:
   - Recommended EC2 instance type: `r3.large` or `m3.medium` (doesn't require much resources)
   - Approximate run time: ~1 hour 20-40 minutes
   - Command:  
      ```scala
      ohnosequences.db.rna16s.test.rna16s.clusterSequences(your_user.AWSUser)
      ```
      It returns you the instance ID. You have to terminate it  **manually**.

7. `dropInconsistentAssignments`:
   - Recommended EC2 instance type: `r3.large` or `m3.xlarge`
   - Approximate run time: ~10 minutes
   - Command:  
      ```scala
      ohnosequences.db.rna16s.test.rna16s.dropInconsistentAssignmentsAndGenerate(your_user.AWSUser)
      ```
      It returns you the instance ID. You have to terminate it  **manually**.

8. `releaseData`
   - This is not a bundle, just a function that you call locally and it copies objects in S3
   - Approximate run time: ~1 minute
   - Command:
      ```scala
      ohnosequences.db.rna16s.test.releaseData(your_user.AWSUser.profile)
      ```
      It returns you the instance ID. You have to terminate it  **manually**.
