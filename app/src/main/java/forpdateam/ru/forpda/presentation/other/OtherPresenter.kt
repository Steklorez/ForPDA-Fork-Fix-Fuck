package forpdateam.ru.forpda.presentation.other

import forpdateam.ru.forpda.common.mvp.BasePresenter
import forpdateam.ru.forpda.entity.app.CloseableInfo
import forpdateam.ru.forpda.entity.app.other.AppMenuItem
import forpdateam.ru.forpda.entity.remote.profile.ProfileModel
import forpdateam.ru.forpda.model.AuthHolder
import forpdateam.ru.forpda.model.CloseableInfoHolder
import forpdateam.ru.forpda.model.interactors.other.MenuRepository
import forpdateam.ru.forpda.model.repository.auth.AuthRepository
import forpdateam.ru.forpda.model.repository.profile.ProfileRepository
import forpdateam.ru.forpda.presentation.IErrorHandler
import forpdateam.ru.forpda.presentation.ILinkHandler
import forpdateam.ru.forpda.presentation.ISystemLinkHandler
import forpdateam.ru.forpda.presentation.Screen
import forpdateam.ru.forpda.presentation.TabRouter
import moxy.InjectViewState

@InjectViewState
class OtherPresenter(
    private val router: TabRouter,
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val authHolder: AuthHolder,
    private val errorHandler: IErrorHandler,
    private val menuRepository: MenuRepository,
    private val closeableInfoHolder: CloseableInfoHolder,
    private val linkHandler: ILinkHandler,
    private val systemLinkHandler: ISystemLinkHandler
) : BasePresenter<OtherView>() {

    private val closeableInfoIds = arrayOf(
        CloseableInfoHolder.item_other_menu_drag
    )

    private var localMenu = mapOf<Int, List<AppMenuItem>>()
    private val localCloseableInfo = mutableListOf<CloseableInfo>()

    private var profileItem: ProfileModel? = null

    private var isMenuDragMode = false

    override fun onFirstViewAttach() {
        super.onFirstViewAttach()
        subscribeUser()
        authHolder
            .observe()
            .subscribe {
                if (!authHolder.get().isAuth()) {
                    profileItem = null
                }
                updateMenuItems()
            }
            .untilDestroy()

        menuRepository
            .observerMenu()
            .subscribe {
                localMenu = it
                updateMenuItems()
            }
            .untilDestroy()

        closeableInfoHolder
            .observe()
            .subscribe { info ->
                localCloseableInfo.clear()
                localCloseableInfo.addAll(info.filter { closeableInfoIds.contains(it.id) && !it.isClosed })
                updateMenuItems()
            }
            .untilDestroy()
    }

    fun onMenuDragModeChange(isDragMode: Boolean) {
        isMenuDragMode = isDragMode
        updateMenuItems()
    }

    private fun updateMenuItems() {
        if (!isMenuDragMode) {
            viewState.showItems(profileItem, localCloseableInfo, localMenu.map { it.value })
        }
    }

    private fun subscribeUser() {
        profileRepository
            .loadSelf()
            .subscribe({}, {})
            .untilDestroy()
        profileRepository
            .observeCurrentUser()
            .subscribe {
                profileItem = it.value
                updateMenuItems()
            }
            .untilDestroy()
    }

    fun signOut() {
        authRepository
            .signOut()
            .subscribe({
                router.showSystemMessage("Данные авторизации удалены")
            }, {
                errorHandler.handle(it)
            })
            .untilDestroy()
    }

    fun onMenuClick(item: AppMenuItem) {
        if (item.screen != null) {
            item.screen.also {
                router.navigateTo(it)
            }
            menuRepository.setLastOpened(item.id)
        } else {
            when (item.id) {
                MenuRepository.item_link_github -> {
                    systemLinkHandler.handle("https://github.com/Steklorez/ForPDA-Fork-Fix-Fuck")
                }
            }

        }
    }

    fun onProfileClick() {
        if (authHolder.get().isAuth()) {
            router.navigateTo(Screen.Profile())
        } else {
            router.navigateTo(Screen.Auth())
        }
    }

    fun onChangeMenuSequence(items: List<AppMenuItem>) {
        menuRepository.setMainMenuSequence(items)
    }

    fun onCloseInfo(item: CloseableInfo) {
        closeableInfoHolder.close(item)
    }
}
