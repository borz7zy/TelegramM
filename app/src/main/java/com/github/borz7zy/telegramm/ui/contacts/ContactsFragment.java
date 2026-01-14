package com.github.borz7zy.telegramm.ui.contacts;

import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.borz7zy.telegramm.R;
import com.github.borz7zy.telegramm.actor.AbstractActor;
import com.github.borz7zy.telegramm.ui.base.BaseTdFragment;

public class ContactsFragment extends BaseTdFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_contacts, container, false);
    }

    @Override
    protected AbstractActor createActor() {
        return null;
    }
}