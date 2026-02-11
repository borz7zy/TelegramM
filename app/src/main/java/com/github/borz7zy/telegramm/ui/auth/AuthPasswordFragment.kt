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
import org.drinkless.tdlib.TdApi.CheckAuthenticationPassword

class AuthPasswordFragment : BaseTelegramFragment() {
    private var passwordEdit: EditText? = null
    private var nextBtn: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_auth_password, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        passwordEdit = view.findViewById<EditText>(R.id.passwordEdit)
        nextBtn = view.findViewById<Button>(R.id.nextBtnPwd)

        if (getArguments() != null) { // TODO
            val hint = requireArguments().getString("arg_password_hint")
            if (hint != null && !hint.isEmpty()) {
                passwordEdit!!.setHint(hint)
            }
        }

        nextBtn!!.setOnClickListener(View.OnClickListener { v: View? ->
            val pwd = passwordEdit!!.getText().toString()
            session.send(CheckAuthenticationPassword(pwd))
        })
    }

    override fun onAuthStateChanged(state: AuthorizationState?) {
        val nav = findNavController(requireView())
        if (state is AuthorizationStateReady) {
            nav.navigate(R.id.frag_pass_to_main)
        } else {
            // TODO
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}