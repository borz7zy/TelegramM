package com.github.borz7zy.telegramm.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.navigation.Navigation.findNavController
import com.github.borz7zy.telegramm.R
import com.github.borz7zy.telegramm.ui.base.BaseTelegramFragment
import org.drinkless.tdlib.TdApi.AuthorizationState
import org.drinkless.tdlib.TdApi.AuthorizationStateReady
import org.drinkless.tdlib.TdApi.AuthorizationStateWaitCode
import org.drinkless.tdlib.TdApi.AuthorizationStateWaitPassword
import org.drinkless.tdlib.TdApi.SetAuthenticationPhoneNumber

class AuthPhoneFragment : BaseTelegramFragment() {
    private var phoneEdit: EditText? = null
    private var nextBtn: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_auth_phone, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        phoneEdit = view.findViewById<EditText>(R.id.phoneEdit)
        nextBtn = view.findViewById<Button>(R.id.nextBtn)

        nextBtn!!.setOnClickListener(View.OnClickListener { v: View? ->
            val phone = phoneEdit!!.getText().toString()
            if (session == null) {
                Toast.makeText(requireContext(), "Session not ready", Toast.LENGTH_SHORT).show()
                return@OnClickListener
            }
            if (phone.isEmpty()) {
                return@OnClickListener
            }
            val req =
                SetAuthenticationPhoneNumber()
            req.phoneNumber = phone
            session.send(req)
        })
    }

    override fun onAuthStateChanged(state: AuthorizationState?) {
        val nav = findNavController(requireView())
        if (state is AuthorizationStateWaitCode) {
            nav.navigate(R.id.frag_phone_to_code)
        } else if (state is AuthorizationStateWaitPassword) {
            nav.navigate(R.id.frag_phone_to_password)
        } else if (state is AuthorizationStateReady) {
            // TODO
            nav.navigate(R.id.frag_phone_to_main)
        } else {
            // TODO
        }
    }

    override fun onAuthorized() {
        val nav = findNavController(requireView())
        // TODO
        nav.navigate(R.id.frag_phone_to_main)
    }

    companion object {
        private const val TAG = "AuthPhoneFragment"
    }
}
