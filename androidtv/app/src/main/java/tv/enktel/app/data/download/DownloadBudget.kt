package tv.enktel.app.data.download

/**
 * How many downloads may run at once, and how wide each one may be.
 *
 * ### The report
 *
 * > Download speeds and connections keep getting metered and slow down,
 * > having users manually pause and continue downloads every few seconds to
 * > keep the speed up.
 *
 * Nothing limited how many downloads ran at the same time. [DownloadHub]'s
 * `enqueueMany` — which is what "Download season" and "Download entire series"
 * call — walked the list and started every entry, 60 ms apart. Each of those
 * then opened up to four concurrent ranged GETs of its own.
 *
 * A twenty-episode season is therefore eighty simultaneous requests at a panel
 * that sells one, two or three connections. The surplus sockets are refused or
 * reset the instant they open; [ParallelDownloader]'s adaptive throttle reads
 * that — correctly — as a connection cap and ratchets each download down to a
 * single stream; and the line spends its capacity on handshakes for sockets
 * that are about to be hung up on. Everything crawls, and it crawls worse the
 * more the viewer asked for.
 *
 * Pausing a few downloads frees the line and the rest speed up. That is the
 * workaround in the report, and it is the viewer hand-operating the admission
 * control this object exists to be.
 *
 * ### Why one download at a time is the right answer, not a compromise
 *
 * On a line with four connections the choice is twenty episodes at a twentieth
 * of the speed or one episode at full speed. They finish the whole season at
 * the same moment, but the second one has the first episode on disk in a few
 * minutes rather than all twenty arriving at the end. Only a line with more
 * connections than one download can use gets a second concurrent download.
 *
 * Pure, and [DownloadBudgetTest] pins it: this decides whether a season
 * download is usable, and it is the kind of arithmetic that is easy to get
 * subtly wrong and impossible to notice from the code.
 */
object DownloadBudget {

    /**
     * Sockets assumed when the panel publishes no cap.
     *
     * Every plain M3U line and plenty of Xtream panels report nothing. Four
     * is what the downloader has always assumed in that case, and it is below
     * the per-IP caps these panels typically enforce.
     */
    const val DEFAULT_BUDGET = 4

    /** Ceiling on range workers in one download, from [ParallelDownloader]. */
    const val MAX_STREAMS = 4

    /** What to run right now. */
    data class Plan(
        /** How many downloads may be in flight together. Never below 1. */
        val concurrent: Int,
        /** Range workers each of those may open. Never below 1. */
        val streamsEach: Int,
    ) {
        /** Sockets this plan will actually ask the line for. */
        val sockets: Int get() = concurrent * streamsEach
    }

    /**
     * The plan for a line that publishes [maxConnections].
     *
     * One connection is held back whenever the panel names a cap, because the
     * viewer pressing play on a live channel while a download runs must not be
     * refused by their own downloads. A line that publishes nothing keeps the
     * long-standing assumption of four rather than inventing a reservation
     * against a number nobody stated.
     */
    fun plan(maxConnections: Int): Plan {
        val budget =
            if (maxConnections <= 0) DEFAULT_BUDGET
            else (maxConnections - 1).coerceAtLeast(1)
        val streamsEach = budget.coerceIn(1, MAX_STREAMS)
        // Integer division on purpose: a budget of six runs one download at
        // four streams rather than two at three, because a download that is
        // finishing is worth more than two that are halfway.
        val concurrent = (budget / streamsEach).coerceAtLeast(1)
        return Plan(concurrent = concurrent, streamsEach = streamsEach)
    }

    /**
     * How many more downloads may start, given how many are already running.
     *
     * Negative never happens, but clamping says so rather than leaving the
     * caller to find out: [DownloadHub] reconciles orphaned rows at start-up
     * and can briefly believe more are running than a plan allows.
     */
    fun slotsFree(plan: Plan, running: Int): Int =
        (plan.concurrent - running).coerceAtLeast(0)
}
