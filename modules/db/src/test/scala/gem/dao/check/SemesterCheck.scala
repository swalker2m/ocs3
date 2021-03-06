// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem.dao
package check

class SemesterCheck extends Check {
  import SemesterDao.Statements._
  "SemesterDao.Statements" should
            "canonicalize" in check(canonicalize(Dummy.semester))
}
