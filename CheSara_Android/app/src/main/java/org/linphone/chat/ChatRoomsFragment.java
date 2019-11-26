package org.linphone.chat;

/*
ChatRoomsFragment.java
Copyright (C) 2017 Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.ListResult;
import com.google.firebase.storage.StorageReference;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import org.linphone.LinphoneManager;
import org.linphone.R;
import org.linphone.activities.AboutActivity;
import org.linphone.call.CallActivity;
import org.linphone.contacts.ContactsManager;
import org.linphone.contacts.ContactsUpdatedListener;
import org.linphone.core.ChatMessage;
import org.linphone.core.ChatRoom;
import org.linphone.core.ChatRoomListenerStub;
import org.linphone.core.Core;
import org.linphone.core.CoreListenerStub;
import org.linphone.core.ProxyConfig;
import org.linphone.utils.LinphoneUtils;
import org.linphone.utils.SelectableHelper;
import org.linphone.views.LinphoneLinearLayoutManager;

public class ChatRoomsFragment extends Fragment
        implements ContactsUpdatedListener,
                ChatRoomViewHolder.ClickListener,
                SelectableHelper.DeleteListener {

    private RecyclerView mChatRoomsList;
    private ImageView mNewGroupDiscussionButton;
    private ImageView mBackToCallButton;
    private ChatRoomsAdapter mChatRoomsAdapter;
    private CoreListenerStub mListener;
    private RelativeLayout mWaitLayout;
    private int mChatRoomDeletionPendingCount;
    private ChatRoomListenerStub mChatRoomListener;
    private SelectableHelper mSelectionHelper;
    private TextView mNoChatHistory;
    ListView listView;
    StorageReference listRef;
    FirebaseStorage storage;
    String itemValue;
    InputStream inputStream;
    StorageReference storageRef;
    String data;
    Uri aUri;

    @Override
    public View onCreateView(
            final LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = inflater.inflate(R.layout.chatlist, container, false);

        mChatRoomsList = view.findViewById(R.id.chatList);
        mWaitLayout = view.findViewById(R.id.waitScreen);
        ImageView newDiscussionButton = view.findViewById(R.id.new_discussion);
        mNewGroupDiscussionButton = view.findViewById(R.id.new_group_discussion);
        mBackToCallButton = view.findViewById(R.id.back_in_call);
        mNoChatHistory = view.findViewById(R.id.noChatHistory);

        ChatRoom[] rooms = LinphoneManager.getCore().getChatRooms();
        List<ChatRoom> mRooms;
        if (getResources().getBoolean(R.bool.hide_empty_one_to_one_chat_rooms)) {
            mRooms = LinphoneUtils.removeEmptyOneToOneChatRooms(rooms);
        } else {
            mRooms = Arrays.asList(rooms);
        }

        mSelectionHelper = new SelectableHelper(view, this);
        mChatRoomsAdapter =
                new ChatRoomsAdapter(
                        getActivity(), R.layout.chatlist_cell, mRooms, this, mSelectionHelper);

        mChatRoomsList.setAdapter(mChatRoomsAdapter);
        mSelectionHelper.setAdapter(mChatRoomsAdapter);
        mSelectionHelper.setDialogMessage(R.string.chat_room_delete_dialog);

        LinearLayoutManager layoutManager = new LinphoneLinearLayoutManager(getActivity());
        mChatRoomsList.setLayoutManager(layoutManager);

        DividerItemDecoration dividerItemDecoration =
                new DividerItemDecoration(
                        mChatRoomsList.getContext(), layoutManager.getOrientation());
        dividerItemDecoration.setDrawable(
                getActivity()
                        .getApplicationContext()
                        .getResources()
                        .getDrawable(R.drawable.divider));
        mChatRoomsList.addItemDecoration(dividerItemDecoration);

        mWaitLayout.setVisibility(View.GONE);

        newDiscussionButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((ChatActivity) getActivity())
                                .showChatRoomCreation(null, null, null, false, false);
                    }
                });

        mNewGroupDiscussionButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((ChatActivity) getActivity())
                                .showChatRoomCreation(null, null, null, false, true);
                    }
                });

        mBackToCallButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(getActivity(), CallActivity.class));
                    }
                });

        mListener =
                new CoreListenerStub() {
                    @Override
                    public void onMessageSent(Core core, ChatRoom room, ChatMessage message) {
                        refreshChatRoom(room);
                    }

                    @Override
                    public void onMessageReceived(Core core, ChatRoom cr, ChatMessage message) {
                        refreshChatRoom(cr);
                    }

                    @Override
                    public void onMessageReceivedUnableDecrypt(
                            Core core, ChatRoom room, ChatMessage message) {
                        refreshChatRoom(room);
                    }

                    @Override
                    public void onChatRoomRead(Core core, ChatRoom room) {
                        refreshChatRoom(room);
                    }

                    @Override
                    public void onChatRoomStateChanged(
                            Core core, ChatRoom cr, ChatRoom.State state) {
                        if (state == ChatRoom.State.Created) {
                            refreshChatRoom(cr);
                        }
                    }
                };

        mChatRoomListener =
                new ChatRoomListenerStub() {
                    @Override
                    public void onStateChanged(ChatRoom room, ChatRoom.State state) {
                        super.onStateChanged(room, state);
                        if (state == ChatRoom.State.Deleted
                                || state == ChatRoom.State.TerminationFailed) {
                            mChatRoomDeletionPendingCount -= 1;

                            if (state == ChatRoom.State.TerminationFailed) {
                                // TODO error message
                            }

                            if (mChatRoomDeletionPendingCount == 0) {
                                mWaitLayout.setVisibility(View.GONE);
                                refreshChatRoomsList();
                            }
                        }
                    }
                };

        return view;
    }

    @Override
    public void onItemClicked(int position) {
        if (mChatRoomsAdapter.isEditionEnabled()) {
            mChatRoomsAdapter.toggleSelection(position);
        } else {
            ChatRoom room = (ChatRoom) mChatRoomsAdapter.getItem(position);
            if (room != null) {
                ((ChatActivity) getActivity())
                        .showChatRoom(room.getLocalAddress(), room.getPeerAddress());
                refreshChatRoom(room);
            }
        }
    }

    @Override
    public boolean onItemLongClicked(int position) {
        if (!mChatRoomsAdapter.isEditionEnabled()) {
            mSelectionHelper.enterEditionMode();
        }
        mChatRoomsAdapter.toggleSelection(position);
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        ContactsManager.getInstance().addContactsListener(this);

        mBackToCallButton.setVisibility(View.INVISIBLE);
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.addListener(mListener);

            if (core.getCallsNb() > 0) {
                mBackToCallButton.setVisibility(View.VISIBLE);
            }
        }

        refreshChatRoomsList();

        ProxyConfig lpc = core.getDefaultProxyConfig();
        mNewGroupDiscussionButton.setVisibility(
                (lpc != null && lpc.getConferenceFactoryUri() != null) ? View.VISIBLE : View.GONE);

        listView = (ListView) getActivity().findViewById(R.id.filelist);
        storage = FirebaseStorage.getInstance("gs://mvoipcall-cc6e3.appspot.com");
        listRef = storage.getReference();

        final ArrayList<String> fileList = new ArrayList<String>();
        // Now we get the references of these images
        listRef.listAll()
                .addOnSuccessListener(
                        new OnSuccessListener<ListResult>() {
                            @Override
                            public void onSuccess(ListResult result) {
                                for (StorageReference fileRef : result.getItems()) {
                                    if (fileRef.getName().contains(".txt")) {
                                        fileList.add(fileRef.getName());
                                    }
                                }
                                ArrayAdapter<String> adapter =
                                        new ArrayAdapter<String>(
                                                getActivity(),
                                                android.R.layout.simple_list_item_1,
                                                android.R.id.text1,
                                                fileList);

                                // Assign adapter to ListView
                                listView.setAdapter(adapter);

                                // ListView Item Click Listener
                                listView.setOnItemClickListener(
                                        new AdapterView.OnItemClickListener() {

                                            @Override
                                            public void onItemClick(
                                                    AdapterView<?> parent,
                                                    View view,
                                                    int position,
                                                    long id) {
                                                int itemPosition = position;
                                                itemValue =
                                                        (String)
                                                                listView.getItemAtPosition(
                                                                        position);

                                                storageRef = listRef.child(itemValue);
                                                storageRef
                                                        .getDownloadUrl()
                                                        .addOnSuccessListener(
                                                                new OnSuccessListener<Uri>() {

                                                                    @Override
                                                                    public void onSuccess(Uri uri) {

                                                                        //
                                                                        //                      try{
                                                                        //
                                                                        //
                                                                        // inputStream = new
                                                                        // FileInputStream(new
                                                                        // File(uri.getPath()));
                                                                        //
                                                                        //
                                                                        //
                                                                        // ByteArrayOutputStream
                                                                        // byteArrayOutputStream =
                                                                        // new
                                                                        // ByteArrayOutputStream();
                                                                        //
                                                                        //
                                                                        //
                                                                        // int i;
                                                                        //
                                                                        //
                                                                        // try {
                                                                        //
                                                                        //
                                                                        //
                                                                        //    i =
                                                                        // inputStream.read();
                                                                        //
                                                                        //
                                                                        //    while (i != -1) {
                                                                        //
                                                                        //
                                                                        //
                                                                        // byteArrayOutputStream.write(i);
                                                                        //
                                                                        //
                                                                        //        i =
                                                                        // inputStream.read();
                                                                        //
                                                                        //
                                                                        //    }
                                                                        //
                                                                        //
                                                                        //
                                                                        //    data = new
                                                                        // String(byteArrayOutputStream.toByteArray(), "UTF-8");
                                                                        //
                                                                        //
                                                                        //    inputStream.close();
                                                                        //
                                                                        //
                                                                        // } catch (IOException e) {
                                                                        //
                                                                        //
                                                                        //    e.printStackTrace();
                                                                        //
                                                                        //
                                                                        // }
                                                                        //
                                                                        //
                                                                        //
                                                                        // Log.d("뭘까", data);
                                                                        ////
                                                                        //
                                                                        // Intent intent = new
                                                                        // Intent(getActivity(),
                                                                        // AboutActivity.class);
                                                                        ////
                                                                        //
                                                                        // intent.putExtra("filename", itemValue);
                                                                        ////
                                                                        //
                                                                        // intent.putExtra("content", data);
                                                                        ////
                                                                        //
                                                                        // startActivity(intent);
                                                                        //
                                                                        //
                                                                        //
                                                                        //
                                                                        // }catch (IOException e){}

                                                                        aUri = uri;
                                                                        new Thread(
                                                                                        new Runnable() {
                                                                                            @Override
                                                                                            public
                                                                                            void
                                                                                                    run() {
                                                                                                try {

                                                                                                    URL
                                                                                                            url =
                                                                                                                    new URL(
                                                                                                                            aUri
                                                                                                                                    .toString()); // my app link change it

                                                                                                    HttpsURLConnection
                                                                                                            uc =
                                                                                                                    (HttpsURLConnection)
                                                                                                                            url
                                                                                                                                    .openConnection();
                                                                                                    //                                                            BufferedReader br = new BufferedReader(new InputStreamReader(uc.getInputStream(), "utf-8"));
                                                                                                    //                                                            String line;
                                                                                                    //                                                            StringBuilder lin2 = new StringBuilder();
                                                                                                    //                                                            while ((line = br.readLine()) != null)
                                                                                                    //                                                            {
                                                                                                    //                                                              Log.d("wweew", line);
                                                                                                    //                                                                lin2.append(line);
                                                                                                    //                                                            }

                                                                                                    //                                                            BufferedReader inputStream = new BufferedReader(new InputStreamReader(uc.getInputStream(), "UTF-8"));
                                                                                                    InputStream
                                                                                                            inputStream =
                                                                                                                    uc
                                                                                                                            .getInputStream();
                                                                                                    ByteArrayOutputStream
                                                                                                            byteArrayOutputStream =
                                                                                                                    new ByteArrayOutputStream();

                                                                                                    int
                                                                                                            i;
                                                                                                    data =
                                                                                                            null;
                                                                                                    try {
                                                                                                        i =
                                                                                                                inputStream
                                                                                                                        .read();
                                                                                                        while (i
                                                                                                                != -1) {
                                                                                                            byteArrayOutputStream
                                                                                                                    .write(
                                                                                                                            i);
                                                                                                            i =
                                                                                                                    inputStream
                                                                                                                            .read();
                                                                                                        }

                                                                                                        data =
                                                                                                                new String(
                                                                                                                        byteArrayOutputStream
                                                                                                                                .toByteArray(),
                                                                                                                        "EUC-KR");
                                                                                                        inputStream
                                                                                                                .close();
                                                                                                    } catch (
                                                                                                            IOException
                                                                                                                    e) {
                                                                                                        e
                                                                                                                .printStackTrace();
                                                                                                    }

                                                                                                    Intent
                                                                                                            intent =
                                                                                                                    new Intent(
                                                                                                                            getActivity(),
                                                                                                                            AboutActivity
                                                                                                                                    .class);
                                                                                                    intent
                                                                                                            .putExtra(
                                                                                                                    "filename",
                                                                                                                    itemValue);
                                                                                                    intent
                                                                                                            .putExtra(
                                                                                                                    "content",
                                                                                                                    data);
                                                                                                    //                                                            intent.putExtra("content", lin2.toString());
                                                                                                    startActivity(
                                                                                                            intent);

                                                                                                } catch (
                                                                                                        IOException
                                                                                                                e) {
                                                                                                    e
                                                                                                            .printStackTrace();
                                                                                                }
                                                                                            }
                                                                                        })
                                                                                .start();
                                                                    }
                                                                })
                                                        .addOnFailureListener(
                                                                new OnFailureListener() {
                                                                    @Override
                                                                    public void onFailure(
                                                                            @NonNull
                                                                                    Exception
                                                                                            exception) {
                                                                        // Handle any errors
                                                                    }
                                                                });
                                            }
                                        });
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(Exception exception) {
                                // Handle any errors
                            }
                        });
    }

    @Override
    public void onPause() {
        Core core = LinphoneManager.getCore();
        if (core != null) {
            core.removeListener(mListener);
        }
        ContactsManager.getInstance().removeContactsListener(this);
        mChatRoomsAdapter.clear();
        super.onPause();
    }

    @Override
    public void onDeleteSelection(Object[] objectsToDelete) {
        Core core = LinphoneManager.getCore();
        mChatRoomDeletionPendingCount = objectsToDelete.length;
        for (Object obj : objectsToDelete) {
            ChatRoom room = (ChatRoom) obj;
            room.addListener(mChatRoomListener);
            core.deleteChatRoom(room);
        }
        if (mChatRoomDeletionPendingCount > 0) {
            mWaitLayout.setVisibility(View.VISIBLE);
        }
        ((ChatActivity) getActivity()).displayMissedChats();
    }

    @Override
    public void onContactsUpdated() {
        ChatRoomsAdapter adapter = (ChatRoomsAdapter) mChatRoomsList.getAdapter();
        if (adapter != null) {
            adapter.refresh();
        }
    }

    private void refreshChatRoom(ChatRoom cr) {
        ChatRoomViewHolder holder = (ChatRoomViewHolder) cr.getUserData();
        if (holder != null) {
            int position = holder.getAdapterPosition();
            if (position == 0) {
                mChatRoomsAdapter.notifyItemChanged(0);
            } else {
                refreshChatRoomsList();
            }
        } else {
            refreshChatRoomsList();
        }
    }

    private void refreshChatRoomsList() {
        //        mChatRoomsAdapter.refresh();
        //        mNoChatHistory.setVisibility(
        //                mChatRoomsAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    public String getString(byte[] input) {

        StringBuffer rtn = new StringBuffer();

        for (int i = 0; i < input.length; ) {
            if ((input[i] & 0x80) == 0x80) {

                byte[] hangle = new byte[2];

                hangle[0] = input[i];

                hangle[1] = input[++i];

                rtn.append(new String(hangle));

            } else rtn.append((char) input[i]);
            ++i;
        }
        return rtn.toString();
    }
}
