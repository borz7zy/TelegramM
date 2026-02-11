package com.github.borz7zy.telegramm.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.navigation.Navigation.findNavController
import com.github.borz7zy.telegramm.R
import com.github.borz7zy.telegramm.ui.base.BaseTelegramFragment
import org.drinkless.tdlib.TdApi.AuthorizationState
import org.drinkless.tdlib.TdApi.AuthorizationStateReady
import org.drinkless.tdlib.TdApi.AuthorizationStateWaitPassword
import org.drinkless.tdlib.TdApi.CheckAuthenticationCode

class AuthCodeFragment : BaseTelegramFragment() {
    private var codeEdit: EditText? = null
    private var nextBtn: Button? = null


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_auth_code, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        codeEdit = view.findViewById<EditText>(R.id.codeEdit)
        nextBtn = view.findViewById<Button>(R.id.nextBtn)

        nextBtn!!.setOnClickListener(View.OnClickListener { v: View? ->
            val phone = codeEdit!!.getText().toString()
            session.send(CheckAuthenticationCode(phone))
        })
    }

    override fun onAuthStateChanged(state: AuthorizationState?) {
        val nav = findNavController(requireView())
        if (state is AuthorizationStateWaitPassword) {
            nav.navigate(R.id.frag_code_to_password)
        } else if (state is AuthorizationStateReady) {
            nav.navigate(R.id.frag_code_to_password)
        } else {
            // TODO
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}