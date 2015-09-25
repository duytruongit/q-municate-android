package com.quickblox.q_municate.ui.activities.main;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.facebook.Session;
import com.facebook.SessionState;
import com.quickblox.chat.QBChatService;
import com.quickblox.q_municate.R;
import com.quickblox.q_municate.core.gcm.GSMHelper;
import com.quickblox.q_municate.ui.activities.base.BaseLogeableActivity;
import com.quickblox.q_municate.ui.activities.chats.NewDialogActivity;
import com.quickblox.q_municate.ui.fragments.chats.DialogsListFragment;
import com.quickblox.q_municate.ui.fragments.feedback.FeedbackFragment;
import com.quickblox.q_municate.ui.fragments.invitefriends.InviteFriendsFragment;
import com.quickblox.q_municate.ui.fragments.settings.SettingsFragment;
import com.quickblox.q_municate.utils.ToastUtils;
import com.quickblox.q_municate.utils.helpers.FacebookHelper;
import com.quickblox.q_municate.utils.helpers.ImportFriendsHelper;
import com.quickblox.q_municate_core.core.command.Command;
import com.quickblox.q_municate_core.models.AppSession;
import com.quickblox.q_municate_core.qb.commands.QBLoadDialogsCommand;
import com.quickblox.q_municate_core.qb.commands.QBLoginChatCompositeCommand;
import com.quickblox.q_municate_core.service.QBServiceConsts;
import com.quickblox.q_municate_db.managers.DataManager;

public class MainActivity extends BaseLogeableActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private FacebookHelper facebookHelper;
    private ImportFriendsHelper importFriendsHelper;
    private GSMHelper gsmHelper;

    private LoginChatCompositeSuccessAction loginChatCompositeSuccessAction;
    private ImportFriendsSuccessAction importFriendsSuccessAction;
    private ImportFriendsFailAction importFriendsFailAction;

    public static void start(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        activateButterKnife();

        initActionBar();
        initFields(savedInstanceState);

        checkGCMRegistration();

        if (!isLoggedInChat()) {
            loginChat();
        }

        launchDialogsListFragment();
    }

    private void initFields(Bundle savedInstanceState) {
        gsmHelper = new GSMHelper(this);
        loginChatCompositeSuccessAction = new LoginChatCompositeSuccessAction();
        importFriendsSuccessAction = new ImportFriendsSuccessAction();
        importFriendsFailAction = new ImportFriendsFailAction();

        if (!appSharedHelper.isUsersImportInitialized()) {
            showProgress();
            facebookHelper = new FacebookHelper(this, savedInstanceState, new FacebookSessionStatusCallback());
            importFriendsHelper = new ImportFriendsHelper(MainActivity.this, facebookHelper);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add_chat:
                boolean isFriends = !DataManager.getInstance().getFriendDataManager().getAll().isEmpty();
                if (isFriends) {
                    NewDialogActivity.start(this);
                } else {
                    ToastUtils.longToast(R.string.ndl_no_friends_for_new_chat);
                }
                break;
            case R.id.action_start_invite_friends:
                launchInviteFriendsFragment();
                break;
            case R.id.action_start_feedback:
                launchFeedbackFragment();
                break;
            case R.id.action_start_settings:
                launchSettingsFragment();
                break;
            case R.id.action_start_about:
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (currentFragment instanceof InviteFriendsFragment) {
            currentFragment.onActivityResult(requestCode, resultCode, data);
        } else if (facebookHelper != null) {
            facebookHelper.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void loginChat() {
        QBLoginChatCompositeCommand.start(this);
    }

    private void performImportFriendsSuccessAction() {
        appSharedHelper.saveUsersImportInitialized(true);
    }

    private void performLoginChatSuccessAction(Bundle bundle) {
        checkLoadDialogs();
        hideProgress();
    }

    private void checkLoadDialogs() {
        if (appSharedHelper.isFirstAuth()) {
            QBLoadDialogsCommand.start(this);
        }
    }

    private void checkGCMRegistration() {
        if (gsmHelper.checkPlayServices()) {
            if (!gsmHelper.isDeviceRegisteredWithUser(AppSession.getSession().getUser())) {
                gsmHelper.registerInBackground();
            }
        } else {
            Log.i(TAG, "No valid Google Play Services APK found.");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        addActions();
        checkGCMRegistration();
    }

    @Override
    protected void onPause() {
        super.onPause();
        removeActions();
    }

    private void addActions() {
        addAction(QBServiceConsts.LOGIN_CHAT_COMPOSITE_SUCCESS_ACTION, loginChatCompositeSuccessAction);
        addAction(QBServiceConsts.IMPORT_FRIENDS_SUCCESS_ACTION, importFriendsSuccessAction);
        addAction(QBServiceConsts.IMPORT_FRIENDS_FAIL_ACTION, importFriendsFailAction);
        addAction(QBServiceConsts.LOAD_CHATS_DIALOGS_SUCCESS_ACTION, new LoadChatsSuccessAction());

        updateBroadcastActionList();
    }

    private void removeActions() {
        removeAction(QBServiceConsts.IMPORT_FRIENDS_FAIL_ACTION);
        updateBroadcastActionList();
    }

    private void performImportFriendsFailAction(Bundle bundle) {
        performImportFriendsSuccessAction();
    }

    private boolean isLoggedInChat() {
        return QBChatService.isInitialized() && QBChatService.getInstance().isLoggedIn();
    }

    private void performLoadChatsSuccessAction(Bundle bundle) {
        appSharedHelper.saveFirstAuth(false);
    }

    private void launchDialogsListFragment() {
        setCurrentFragment(DialogsListFragment.newInstance());
    }

    private void launchInviteFriendsFragment() {
        setCurrentFragment(InviteFriendsFragment.newInstance());
    }

    private void launchSettingsFragment() {
        setCurrentFragment(SettingsFragment.newInstance());
    }

    private void launchFeedbackFragment() {
        setCurrentFragment(FeedbackFragment.newInstance());
    }

    private class FacebookSessionStatusCallback implements Session.StatusCallback {

        @Override
        public void call(Session session, SessionState state, Exception exception) {
            if (session.isOpened()) {
                importFriendsHelper.startGetFriendsListTask(true);
            } else if (!session.isClosed() && !appSharedHelper.isUsersImportInitialized()) {
                importFriendsHelper.startGetFriendsListTask(false);
                hideProgress();
            }
        }
    }

    private class LoginChatCompositeSuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            performLoginChatSuccessAction(bundle);
        }
    }

    private class ImportFriendsSuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            performImportFriendsSuccessAction();
        }
    }

    private class ImportFriendsFailAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            performImportFriendsFailAction(bundle);
        }
    }

    private class LoadChatsSuccessAction implements Command {

        @Override
        public void execute(Bundle bundle) {
            performLoadChatsSuccessAction(bundle);
        }
    }
}