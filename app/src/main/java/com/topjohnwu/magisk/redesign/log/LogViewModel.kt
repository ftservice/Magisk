package com.topjohnwu.magisk.redesign.log

import com.topjohnwu.magisk.BR
import com.topjohnwu.magisk.Config
import com.topjohnwu.magisk.Const
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.data.repository.LogRepository
import com.topjohnwu.magisk.databinding.ComparableRvItem
import com.topjohnwu.magisk.extensions.subscribeK
import com.topjohnwu.magisk.model.binding.BindingAdapter
import com.topjohnwu.magisk.model.entity.recycler.ConsoleItem
import com.topjohnwu.magisk.model.entity.recycler.LogItem
import com.topjohnwu.magisk.model.events.SnackbarEvent
import com.topjohnwu.magisk.redesign.compat.CompatViewModel
import com.topjohnwu.magisk.redesign.home.itemBindingOf
import com.topjohnwu.magisk.redesign.superuser.diffListOf
import com.topjohnwu.superuser.Shell
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.io.File
import java.util.*

class LogViewModel(
    private val repo: LogRepository
) : CompatViewModel() {

    // --- main view

    val items = diffListOf<LogItem>()
    val itemBinding = itemBindingOf<LogItem> {
        it.bindExtra(BR.viewModel, this)
    }

    // --- console

    val consoleAdapter = BindingAdapter()
    val itemsConsole = diffListOf<ComparableRvItem<*>>()
    val itemConsoleBinding = itemBindingOf<ComparableRvItem<*>> {}

    override fun refresh(): Disposable {
        val logs = repo.fetchLogsNowrap()
            .map { it.map { LogItem(it) } }
            .observeOn(Schedulers.computation())
            .map { it to items.calculateDiff(it) }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess {
                items.firstOrNull()?.isTop = false
                items.lastOrNull()?.isBottom = false

                items.update(it.first, it.second)

                items.firstOrNull()?.isTop = true
                items.lastOrNull()?.isBottom = true
            }
            .ignoreElement()

        val console = repo.fetchMagiskLogs()
            .map { ConsoleItem(it) }
            .toList()
            .observeOn(Schedulers.computation())
            .map { it to itemsConsole.calculateDiff(it) }
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess { itemsConsole.update(it.first, it.second) }
            .ignoreElement()

        return Completable.merge(listOf(logs, console)).subscribeK()
    }

    fun saveMagiskLog() {
        val now = Calendar.getInstance()
        val filename = "magisk_log_%04d%02d%02d_%02d%02d%02d.log".format(
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1,
            now.get(Calendar.DAY_OF_MONTH), now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE), now.get(Calendar.SECOND)
        )

        val logFile = File(Config.downloadDirectory, filename)
        runCatching {
            logFile.createNewFile()
        }.onFailure {
            Timber.e(it)
            return
        }

        Shell.su("cat ${Const.MAGISK_LOG} > $logFile").submit {
            SnackbarEvent(logFile.path).publish()
        }
    }

    fun clearMagiskLog() = repo.clearMagiskLogs()
        .subscribeK {
            SnackbarEvent(R.string.logs_cleared).publish()
            requestRefresh()
        }
        .add()

    fun clearLog() = repo.clearLogs()
        .subscribeK {
            SnackbarEvent(R.string.logs_cleared).publish()
            requestRefresh()
        }
        .add()

}